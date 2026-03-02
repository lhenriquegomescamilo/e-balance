package com.ebalance.transactions.application

import arrow.core.Either
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
 * STM-backed interactor for updating a transaction's category.
 *
 * The [inFlight] TVar holds the set of transaction IDs currently undergoing
 * a category update. STM enforces the following invariant atomically:
 *
 *   - [acquire]: if [transactionId] is already in [inFlight], the STM block
 *     calls `retry()` (via [check]), blocking the caller until the set changes.
 *     Once the ID is free the transaction succeeds and the ID is added to the
 *     set — all in a single atomic step.
 *
 *   - [release]: removes the ID from the set, unblocking any caller that
 *     is retrying on the same ID.
 *
 * This guarantees that two concurrent requests for the **same** transaction ID
 * are serialised — the second waits until the first finishes — while requests
 * for **different** IDs never block each other.
 *
 * @param repository    persistence port
 * @param inFlight      shared STM variable tracking in-progress transaction IDs
 */
class UpdateTransactionCategoryInteractor(
    private val repository: TransactionRepository,
    private val inFlight: TVar<Set<Long>>
) : UpdateTransactionCategoryUseCase {

    override suspend fun execute(transactionId: Long, categoryId: Long): Either<TransactionError, Unit> {
        atomically { acquire(transactionId) }
        return try {
            repository.updateTransactionCategory(transactionId, categoryId)
        } finally {
            atomically { release(transactionId) }
        }
    }

    /**
     * STM transaction: blocks (retries) if [id] is already in-flight,
     * then atomically adds it to the set.
     */
    private fun STM.acquire(id: Long) {
        check(id !in inFlight.read()) // retry until ID is no longer in-flight
        inFlight.modify { it + id }
    }

    /**
     * STM transaction: removes [id] from the in-flight set,
     * waking up any blocked [acquire] for the same ID.
     */
    private fun STM.release(id: Long) {
        inFlight.modify { it - id }
    }
}
