package com.ebalance.transactions.domain

/**
 * Port (interface) for the persistence adapter.
 * The domain layer declares WHAT it needs; the infrastructure layer implements HOW.
 */
interface TransactionRepository {
    fun getSummary(filter: TransactionFilter): TransactionSummaryResult
    fun getTransactions(filter: TransactionFilter): TransactionPage
    fun getCategories(): List<CategoryEntry>
    fun getMonthlySummary(filter: TransactionFilter): MonthlySummaryResult
    /** @throws NoSuchElementException if the transaction or category does not exist. */
    fun updateTransactionCategory(transactionId: Long, categoryId: Long)
}
