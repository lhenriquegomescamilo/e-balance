package com.ebalance.transactions.application

import arrow.core.Either
import com.ebalance.transactions.domain.TransactionError
import com.ebalance.transactions.domain.TransactionFilter
import com.ebalance.transactions.domain.TransactionPage
import com.ebalance.transactions.domain.TransactionRepository

/** Input port: paginated list of individual transactions. */
interface GetTransactionsUseCase {
    fun execute(filter: TransactionFilter): Either<TransactionError, TransactionPage>
}

class GetTransactionsInteractor(
    private val repository: TransactionRepository
) : GetTransactionsUseCase {
    override fun execute(filter: TransactionFilter): Either<TransactionError, TransactionPage> =
        repository.getTransactions(filter)
}
