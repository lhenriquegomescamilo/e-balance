package com.ebalance.transactions.application

import arrow.core.left
import arrow.core.right
import com.ebalance.transactions.domain.CategorySummary
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.domain.TransactionSummaryResult
import com.ebalance.transactions.domain.TransactionType
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDate

class GetTransactionSummaryInteractorTest : DescribeSpec({

    val repository = mockk<TransactionRepository>()
    val interactor = GetTransactionSummaryInteractor(repository)

    val defaultFilter = TransactionFilter(
        startDate = LocalDate.of(2026, 1, 1),
        endDate   = LocalDate.of(2026, 3, 31)
    )

    val sampleSummary = TransactionSummaryResult(
        totalIncome       = BigDecimal("3000.00"),
        totalExpenses     = BigDecimal("1200.00"),
        netBalance        = BigDecimal("1800.00"),
        transactionCount  = 15,
        categories        = listOf(
            CategorySummary(
                categoryId       = 1L,
                categoryName     = "Food",
                totalIncome      = BigDecimal.ZERO,
                totalExpenses    = BigDecimal("400.00"),
                transactionCount = 8
            ),
            CategorySummary(
                categoryId       = 2L,
                categoryName     = "Salary",
                totalIncome      = BigDecimal("3000.00"),
                totalExpenses    = BigDecimal.ZERO,
                transactionCount = 1
            )
        )
    )

    describe("execute") {

        it("returns the summary produced by the repository") {
            every { repository.getSummary(defaultFilter) } returns sampleSummary.right()

            val result = interactor.execute(defaultFilter).shouldBeRight()

            result shouldBe sampleSummary
        }

        it("passes the filter to the repository unchanged") {
            val captured = slot<TransactionFilter>()
            every { repository.getSummary(capture(captured)) } returns sampleSummary.right()

            interactor.execute(defaultFilter)

            captured.captured shouldBe defaultFilter
        }

        it("preserves startDate and endDate in the filter forwarded to the repository") {
            val narrowFilter = defaultFilter.copy(
                startDate = LocalDate.of(2026, 2, 1),
                endDate   = LocalDate.of(2026, 2, 28)
            )
            val captured = slot<TransactionFilter>()
            every { repository.getSummary(capture(captured)) } returns sampleSummary.right()

            interactor.execute(narrowFilter)

            captured.captured.startDate shouldBe LocalDate.of(2026, 2, 1)
            captured.captured.endDate   shouldBe LocalDate.of(2026, 2, 28)
        }

        it("preserves categoryIds in the filter forwarded to the repository") {
            val filteredFilter = defaultFilter.copy(categoryIds = listOf(1L, 3L))
            val captured = slot<TransactionFilter>()
            every { repository.getSummary(capture(captured)) } returns sampleSummary.right()

            interactor.execute(filteredFilter)

            captured.captured.categoryIds shouldBe listOf(1L, 3L)
        }

        it("preserves transaction type in the filter forwarded to the repository") {
            val incomeFilter = defaultFilter.copy(type = TransactionType.INCOME)
            val captured = slot<TransactionFilter>()
            every { repository.getSummary(capture(captured)) } returns sampleSummary.right()

            interactor.execute(incomeFilter)

            captured.captured.type shouldBe TransactionType.INCOME
        }

        it("returns a summary with correct totals and category breakdown") {
            every { repository.getSummary(defaultFilter) } returns sampleSummary.right()

            val result = interactor.execute(defaultFilter).shouldBeRight()

            result.totalIncome      shouldBe BigDecimal("3000.00")
            result.totalExpenses    shouldBe BigDecimal("1200.00")
            result.netBalance       shouldBe BigDecimal("1800.00")
            result.transactionCount shouldBe 15
            result.categories       shouldHaveSize 2
        }

        it("returns an empty summary when the repository reports no transactions") {
            val emptySummary = TransactionSummaryResult(
                totalIncome      = BigDecimal.ZERO,
                totalExpenses    = BigDecimal.ZERO,
                netBalance       = BigDecimal.ZERO,
                transactionCount = 0,
                categories       = emptyList()
            )
            every { repository.getSummary(defaultFilter) } returns emptySummary.right()

            val result = interactor.execute(defaultFilter).shouldBeRight()

            result.transactionCount shouldBe 0
            result.categories       shouldBe emptyList()
        }

        it("propagates a DatabaseError returned by the repository") {
            val error = TransactionError.DatabaseError("query failed", RuntimeException("SQL error"))
            every { repository.getSummary(defaultFilter) } returns error.left()

            interactor.execute(defaultFilter).shouldBeLeft() shouldBe error
        }
    }
})
