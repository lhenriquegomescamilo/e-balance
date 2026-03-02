package com.ebalance.transactions.domain

import arrow.core.Either

/**
 * Port (interface) for the persistence adapter.
 * The domain layer declares WHAT it needs; the infrastructure layer implements HOW.
 */
interface TransactionRepository {
    fun getSummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionSummaryResult>
    fun getTransactions(filter: TransactionFilter): Either<TransactionError.DatabaseError, TransactionPage>
    fun getCategories(): Either<TransactionError.DatabaseError, List<CategoryEntry>>
    fun getMonthlySummary(filter: TransactionFilter): Either<TransactionError.DatabaseError, MonthlySummaryResult>
    fun updateTransactionCategory(transactionId: Long, categoryId: Long): Either<TransactionError, Unit>
}
