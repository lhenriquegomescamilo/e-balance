package com.ebalance.transactions.application

import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionRepository
import com.ebalance.transactions.domain.TransactionRow

/** Input port: paginated list of individual transactions. */
interface GetTransactionsUseCase {
    fun execute(filter: TransactionFilter): List<TransactionRow>
}

class GetTransactionsInteractor(
    private val repository: TransactionRepository
) : GetTransactionsUseCase {
    override fun execute(filter: TransactionFilter): List<TransactionRow> =
        repository.getTransactions(filter)
}
