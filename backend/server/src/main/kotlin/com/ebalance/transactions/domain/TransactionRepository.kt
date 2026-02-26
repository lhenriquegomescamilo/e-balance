package com.ebalance.transactions.domain

/**
 * Port (interface) for the persistence adapter.
 * The domain layer declares WHAT it needs; the infrastructure layer implements HOW.
 */
interface TransactionRepository {
    fun getSummary(filter: TransactionFilter): TransactionSummaryResult
    fun getTransactions(filter: TransactionFilter): List<TransactionRow>
    fun getCategories(): List<CategoryEntry>
}
