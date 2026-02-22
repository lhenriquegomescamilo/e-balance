package com.ebalance.application.port

import com.ebalance.domain.model.Transaction

/**
 * Port interface for transaction persistence.
 * Implemented by infrastructure adapters (e.g., SQLite repository).
 */
interface TransactionRepository {
    /**
     * Saves a transaction if it doesn't already exist (idempotent).
     * @param transaction The transaction to save
     * @return true if the transaction was inserted, false if it was a duplicate
     */
    suspend fun save(transaction: Transaction): Boolean

    /**
     * Saves multiple transactions, skipping duplicates.
     * @param transactions The transactions to save
     * @return The number of transactions actually inserted
     */
    suspend fun saveAll(transactions: List<Transaction>): Int

    /**
     * Counts all transactions in the repository.
     */
    suspend fun count(): Long

    /**
     * Finds all transactions ordered by operation date descending.
     */
    suspend fun findAll(): List<Transaction>
}

/**
 * Exception thrown when transaction persistence fails.
 */
class TransactionRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
