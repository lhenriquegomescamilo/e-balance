package com.ebalance.transactions.application

import com.ebalance.transactions.domain.MonthlySummaryResult
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionRepository
import java.time.LocalDate

/** Input port: transactions grouped by category × month for the trend chart. */
interface GetMonthlySummaryUseCase {
    fun execute(filter: TransactionFilter): MonthlySummaryResult
}

class GetMonthlySummaryInteractor(
    private val repository: TransactionRepository
) : GetMonthlySummaryUseCase {
    override fun execute(filter: TransactionFilter): MonthlySummaryResult =
        // Date range is intentionally stripped — the monthly chart always covers
        // the entire database regardless of what the caller passes.
        repository.getMonthlySummary(
            filter.copy(
                startDate = LocalDate.of(1900, 1, 1),
                endDate   = LocalDate.of(2100, 12, 31)
            )
        )
}
