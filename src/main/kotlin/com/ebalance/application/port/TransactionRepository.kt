package com.ebalance.application.port

import arrow.core.Either
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.domain.model.Transaction

/**
 * Port interface for transaction persistence.
 * Implemented by infrastructure adapters (e.g., SQLite repository).
 */
interface TransactionRepository {
    
    /**
     * Result of a save operation.
     */
    data class SaveResult(
        val inserted: Int,
        val duplicates: Int
    )
    
    /**
     * Saves a transaction if it doesn't already exist (idempotent).
     * @param transaction The transaction to save
     * @return Either<TransactionRepositoryError, Boolean> - true if inserted, false if duplicate
     */
    suspend fun save(transaction: Transaction): Either<TransactionRepositoryError, Boolean>

    /**
     * Saves multiple transactions, skipping duplicates.
     * @param transactions The transactions to save
     * @return Either<TransactionRepositoryError, SaveResult>
     */
    suspend fun saveAll(transactions: List<Transaction>): Either<TransactionRepositoryError, SaveResult>

    /**
     * Counts all transactions in the repository.
     * @return Either<TransactionRepositoryError, Long>
     */
    suspend fun count(): Either<TransactionRepositoryError, Long>

    /**
     * Finds all transactions ordered by operation date descending.
     * @return Either<TransactionRepositoryError, List<Transaction>>
     */
    suspend fun findAll(): Either<TransactionRepositoryError, List<Transaction>>
}