package com.ebalance.transactions.application

import arrow.core.left
import arrow.core.right
import arrow.fx.stm.TVar
import arrow.fx.stm.atomically
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class UpdateTransactionCategoryInteractorTest : DescribeSpec({

    /**
     * Creates a fresh interactor with isolated TVars for each test.
     * Returns the interactor plus direct references to the TVars so tests
     * can assert STM side-effects after execution.
     */
    suspend fun makeInteractor(
        repository: TransactionRepository
    ): Triple<UpdateTransactionCategoryInteractor, TVar<Set<Long>>, TVar<Map<Long, Long>>> {
        val inFlight  = TVar.new(emptySet<Long>())
        val committed = TVar.new(emptyMap<Long, Long>())
        return Triple(UpdateTransactionCategoryInteractor(repository, inFlight, committed), inFlight, committed)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    describe("happy path") {

        it("returns Right(Unit) when the repository succeeds") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns Unit.right()
            val (interactor, _, _) = makeInteractor(repository)

            interactor.execute(1L, 42L).shouldBeRight()
        }

        it("persists the new category in committed after a successful update") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns Unit.right()
            val (interactor, _, committed) = makeInteractor(repository)

            interactor.execute(7L, 99L)

            atomically { committed.read() }[7L] shouldBe 99L
        }

        it("releases the in-flight lock after a successful update") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns Unit.right()
            val (interactor, inFlight, _) = makeInteractor(repository)

            interactor.execute(3L, 5L)

            atomically { inFlight.read() } shouldBe emptySet()
        }
    }

    // ── Rollback on domain error ──────────────────────────────────────────────

    describe("rollback on domain error") {

        it("propagates the original error returned by the repository") {
            val repository = mockk<TransactionRepository>()
            val error = TransactionError.NotFound("Transaction 10 not found")
            coEvery { repository.updateTransactionCategory(10L, any()) } returns error.left()
            val (interactor, _, _) = makeInteractor(repository)

            interactor.execute(10L, 1L).shouldBeLeft() shouldBe error
        }

        it("rolls back committed to the previous category when the DB write fails") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns Unit.right()
            val (interactor, _, committed) = makeInteractor(repository)

            // Establish a prior committed state
            interactor.execute(5L, 10L)
            atomically { committed.read() }[5L] shouldBe 10L

            // Fail the next update — committed must revert to the previous value
            coEvery { repository.updateTransactionCategory(5L, 20L) } returns
                TransactionError.NotFound("Transaction 5 not found").left()

            interactor.execute(5L, 20L)

            atomically { committed.read() }[5L] shouldBe 10L
        }

        it("removes the committed entry when there was no prior committed state and the DB write fails") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns
                TransactionError.DatabaseError("connection lost", RuntimeException("timeout")).left()
            val (interactor, _, committed) = makeInteractor(repository)

            interactor.execute(8L, 3L)

            // No previous state → the optimistic write must be fully undone
            atomically { committed.read() }.containsKey(8L) shouldBe false
        }

        it("releases the in-flight lock after a rollback so the same transaction can be retried") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(any(), any()) } returns
                TransactionError.NotFound("Transaction 4 not found").left()
            val (interactor, inFlight, _) = makeInteractor(repository)

            interactor.execute(4L, 7L)

            atomically { inFlight.read() } shouldBe emptySet()
        }
    }

    // ── STM concurrency — serialisation ──────────────────────────────────────

    describe("STM concurrency — serialisation") {

        it("all concurrent calls for the same ID succeed and the lock is fully released") {
            val repository = mockk<TransactionRepository>()
            coEvery { repository.updateTransactionCategory(1L, any()) } returns Unit.right()
            val (interactor, inFlight, committed) = makeInteractor(repository)

            // Five coroutines racing to update the same transaction ID
            coroutineScope {
                (10L..14L).map { catId ->
                    async { interactor.execute(1L, catId) }
                }.awaitAll().forEach { it.shouldBeRight() }
            }

            // STM serialises them one-by-one; after all finish the lock must be free
            atomically { inFlight.read() } shouldBe emptySet()
            // committed must hold the result from whichever call ran last
            atomically { committed.read() }.containsKey(1L) shouldBe true
        }
    }
})
