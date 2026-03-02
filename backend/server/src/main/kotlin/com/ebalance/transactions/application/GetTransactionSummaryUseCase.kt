package com.ebalance.transactions.application

import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.domain.TransactionSummaryResult

/** Input port: aggregated summary with category grouping. */
interface GetTransactionSummaryUseCase {
    fun execute(filter: TransactionFilter): TransactionSummaryResult
}

class GetTransactionSummaryInteractor(
    private val repository: TransactionRepository
) : GetTransactionSummaryUseCase {
    override fun execute(filter: TransactionFilter): TransactionSummaryResult =
        repository.getSummary(filter)
}
