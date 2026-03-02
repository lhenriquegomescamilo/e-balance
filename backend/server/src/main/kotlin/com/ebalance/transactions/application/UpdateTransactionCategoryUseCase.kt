package com.ebalance.transactions.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.stm.STM
import arrow.fx.stm.TVar
import arrow.fx.stm.atomically
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionRepository

/** Input port: update the category assigned to a single transaction. */
interface UpdateTransactionCategoryUseCase {
    suspend fun execute(transactionId: Long, categoryId: Long): Either<TransactionError, Unit>
}

/**
 * STM-backed interactor with optimistic apply and rollback support.
 *
 * Two shared TVars coordinate concurrent calls:
 *
 *   [inFlight]  — set of transaction IDs currently being updated.
 *                 [acquireAndApply] blocks (via STM `retry`) if the same ID is already
 *                 in-flight, serialising concurrent updates for the same transaction.
 *
 *   [committed] — in-memory mirror of the latest committed category per transaction.
 *                 Written optimistically before the DB write so that the new state is
 *                 immediately visible to other STM transactions; rolled back atomically
 *                 if the DB write fails.
 *
 * The method body is expressed with Arrow's [either] builder and [raise] instead of
 * try/catch/finally blocks:
 *   - Exceptions from [atomically] are caught with [runCatching] and re-raised as a
 *     typed [TransactionError] via Arrow's [raise] (possible because [getOrElse] is
 *     `inline`, allowing non-local returns into the enclosing `either` block).
 *   - Domain failures from the repository are handled by inspecting the [Either]
 *     result directly, triggering the correct STM follow-up before re-raising.
 *
 * Phase 1 — acquire + optimistic apply (STM):
 *   Retries until the ID is free, then atomically locks it and writes the new
 *   category to [committed]. Returns the previous category for potential rollback.
 *
 * Phase 2 — DB flush (Either):
 *   [Either.Right] → release the lock; [committed] keeps the new value.
 *   [Either.Left]  → rollback [committed] to the previous category and release
 *                    the lock atomically, then re-raise the domain error.
 */
class UpdateTransactionCategoryInteractor(
    private val repository: TransactionRepository,
    private val inFlight: TVar<Set<Long>>,
    private val committed: TVar<Map<Long, Long>>
) : UpdateTransactionCategoryUseCase {

    override suspend fun execute(transactionId: Long, categoryId: Long): Either<TransactionError, Unit> = either {

        // ── Phase 1: acquire the STM lock and optimistically apply the update ─────
        //
        // acquireAndApply retries (via STM check/retry) until transactionId is free,
        // then atomically: locks it, snapshots the previous category, and writes the
        // new one to `committed`.
        //
        // runCatching wraps the atomically call; getOrElse is inline so raise() can
        // perform a non-local return into this either block, turning any unexpected
        // exception into a typed DatabaseError without a raw catch clause.
        val previousCategoryId: Long? = runCatching { atomically { acquireAndApply(transactionId, categoryId) } }
            .getOrElse { e -> raise(TransactionError.DatabaseError("STM acquire failed", e)) }

        // ── Phase 2: flush to the database ────────────────────────────────────────
        //
        // The repository already returns Either, so we inspect it with `when` and
        // trigger the matching STM follow-up before propagating any error via raise.
        when (val result = repository.updateTransactionCategory(transactionId, categoryId)) {

            is Either.Left -> {
                // Rollback: undo the optimistic committed update and release the lock
                // in one atomic STM step. Best-effort — if the STM call itself fails,
                // suppress that secondary error so the original domain error propagates.
                runCatching { atomically { rollbackAndRelease(transactionId, previousCategoryId) } }
                    .getOrElse { _ -> Unit }

                raise(result.value)
            }

            is Either.Right -> {
                // Commit path: `committed` already holds the new value from Phase 1.
                // Only the in-flight lock needs to be released.
                runCatching { atomically { release(transactionId) } }
                    .getOrElse { e -> raise(TransactionError.DatabaseError("STM release failed", e)) }
            }
        }
    }

    // ── STM operations ────────────────────────────────────────────────────────

    /**
     * Blocks (retries) if [id] is already in-flight, then atomically:
     *  1. Adds [id] to [inFlight].
     *  2. Snapshots the current committed category (`null` = never tracked).
     *  3. Writes [categoryId] to [committed] (optimistic apply).
     *
     * Returns the previous category so the caller can pass it to [rollbackAndRelease].
     */
    private fun STM.acquireAndApply(id: Long, categoryId: Long): Long? {
        check(id !in inFlight.read())
        inFlight.modify { it + id }
        val previous = committed.read()[id]
        committed.modify { it + (id to categoryId) }
        return previous
    }

    /**
     * Atomically reverts [committed] to [previousCategoryId] (or removes the entry
     * if it was never committed before) and removes [id] from [inFlight].
     *
     * Called on the failure path to undo the optimistic apply from [acquireAndApply].
     */
    private fun STM.rollbackAndRelease(id: Long, previousCategoryId: Long?) {
        committed.modify { map ->
            if (previousCategoryId != null) map + (id to previousCategoryId)
            else map - id
        }
        inFlight.modify { it - id }
    }

    /**
     * Removes [id] from [inFlight], waking any caller blocked in [acquireAndApply].
     * Called on the success path; [committed] already holds the new value.
     */
    private fun STM.release(id: Long) {
        inFlight.modify { it - id }
    }
}
