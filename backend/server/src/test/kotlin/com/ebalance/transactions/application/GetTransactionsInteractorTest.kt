package com.ebalance.transactions.application

import arrow.core.left
import arrow.core.right
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionPage
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.domain.TransactionRow
import com.ebalance.transactions.domain.TransactionType
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.LocalDate

class GetTransactionsInteractorTest : DescribeSpec({

    val repository = mockk<TransactionRepository>()
    val interactor = GetTransactionsInteractor(repository)

    val defaultFilter = TransactionFilter(
        startDate = LocalDate.of(2026, 1, 1),
        endDate   = LocalDate.of(2026, 3, 31)
    )

    val sampleRow = TransactionRow(
        id          = 1L,
        operatedAt  = LocalDate.of(2026, 2, 10),
        description = "Supermarket",
        value       = BigDecimal("-42.50"),
        balance     = BigDecimal("1000.00"),
        categoryId  = 3L,
        categoryName = "Food"
    )

    val samplePage = TransactionPage(
        rows       = listOf(sampleRow),
        total      = 1,
        page       = 1,
        pageSize   = 20,
        totalPages = 1
    )

    describe("execute") {

        it("returns the page produced by the repository") {
            every { repository.getTransactions(defaultFilter) } returns samplePage.right()

            val result = interactor.execute(defaultFilter).shouldBeRight()

            result shouldBe samplePage
        }

        it("passes the filter to the repository unchanged") {
            val captured = slot<TransactionFilter>()
            every { repository.getTransactions(capture(captured)) } returns samplePage.right()

            interactor.execute(defaultFilter)

            captured.captured shouldBe defaultFilter
        }

        it("preserves pagination params in the filter forwarded to the repository") {
            val pagedFilter = defaultFilter.copy(page = 3, pageSize = 50)
            val captured = slot<TransactionFilter>()
            every { repository.getTransactions(capture(captured)) } returns samplePage.right()

            interactor.execute(pagedFilter)

            captured.captured.page     shouldBe 3
            captured.captured.pageSize shouldBe 50
        }

        it("preserves categoryIds in the filter forwarded to the repository") {
            val filteredFilter = defaultFilter.copy(categoryIds = listOf(1L, 2L))
            val captured = slot<TransactionFilter>()
            every { repository.getTransactions(capture(captured)) } returns samplePage.right()

            interactor.execute(filteredFilter)

            captured.captured.categoryIds shouldBe listOf(1L, 2L)
        }

        it("preserves transaction type in the filter forwarded to the repository") {
            val expenseFilter = defaultFilter.copy(type = TransactionType.EXPENSE)
            val captured = slot<TransactionFilter>()
            every { repository.getTransactions(capture(captured)) } returns samplePage.right()

            interactor.execute(expenseFilter)

            captured.captured.type shouldBe TransactionType.EXPENSE
        }

        it("returns an empty page when the repository returns no rows") {
            val emptyPage = TransactionPage(rows = emptyList(), total = 0, page = 1, pageSize = 20, totalPages = 0)
            every { repository.getTransactions(defaultFilter) } returns emptyPage.right()

            val result = interactor.execute(defaultFilter).shouldBeRight()

            result.rows shouldBe emptyList()
            result.total shouldBe 0
        }

        it("propagates a DatabaseError returned by the repository") {
            val error = TransactionError.DatabaseError("connection failed", RuntimeException("timeout"))
            every { repository.getTransactions(defaultFilter) } returns error.left()

            interactor.execute(defaultFilter).shouldBeLeft() shouldBe error
        }
    }
})
