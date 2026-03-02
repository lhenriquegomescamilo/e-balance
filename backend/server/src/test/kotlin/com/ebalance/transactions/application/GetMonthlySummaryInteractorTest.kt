package com.ebalance.transactions.application

import com.ebalance.transactions.domain.MonthlySummaryResult
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.domain.TransactionType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate

class GetMonthlySummaryInteractorTest : DescribeSpec({

    val repository = mockk<TransactionRepository>()
    val interactor = GetMonthlySummaryInteractor(repository)

    val emptyResult = MonthlySummaryResult(months = emptyList(), series = emptyList())

    describe("execute") {

        it("always overrides startDate to 1900-01-01 regardless of the caller's filter") {
            val capturedFilter = slot<TransactionFilter>()
            every { repository.getMonthlySummary(capture(capturedFilter)) } returns emptyResult

            interactor.execute(
                TransactionFilter(
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate   = LocalDate.of(2026, 3, 31)
                )
            )

            capturedFilter.captured.startDate shouldBe LocalDate.of(1900, 1, 1)
        }

        it("always overrides endDate to 2100-12-31 regardless of the caller's filter") {
            val capturedFilter = slot<TransactionFilter>()
            every { repository.getMonthlySummary(capture(capturedFilter)) } returns emptyResult

            interactor.execute(
                TransactionFilter(
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate   = LocalDate.of(2026, 3, 31)
                )
            )

            capturedFilter.captured.endDate shouldBe LocalDate.of(2100, 12, 31)
        }

        it("preserves categoryIds from the caller's filter") {
            val capturedFilter = slot<TransactionFilter>()
            every { repository.getMonthlySummary(capture(capturedFilter)) } returns emptyResult

            interactor.execute(
                TransactionFilter(
                    startDate   = LocalDate.of(2026, 1, 1),
                    endDate     = LocalDate.of(2026, 3, 31),
                    categoryIds = listOf(1L, 2L, 3L)
                )
            )

            capturedFilter.captured.categoryIds shouldBe listOf(1L, 2L, 3L)
        }

        it("preserves the type field from the caller's filter") {
            val capturedFilter = slot<TransactionFilter>()
            every { repository.getMonthlySummary(capture(capturedFilter)) } returns emptyResult

            interactor.execute(
                TransactionFilter(
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate   = LocalDate.of(2026, 3, 31),
                    type      = TransactionType.EXPENSE
                )
            )

            capturedFilter.captured.type shouldBe TransactionType.EXPENSE
        }

        it("returns the result produced by the repository") {
            val expectedResult = MonthlySummaryResult(
                months = listOf("2026-01", "2026-02"),
                series = emptyList()
            )
            every { repository.getMonthlySummary(any()) } returns expectedResult

            val result = interactor.execute(
                TransactionFilter(startDate = LocalDate.now(), endDate = LocalDate.now())
            )

            result shouldBe expectedResult
        }
    }
})
