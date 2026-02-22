package com.ebalance.application.port

import com.ebalance.domain.model.Transaction
import java.io.InputStream

/**
 * Port interface for reading transactions from external sources.
 * Implemented by infrastructure adapters (e.g., Excel file reader).
 */
interface TransactionReader {
    /**
     * Reads transactions from the given input stream.
     * @param inputStream The stream containing transaction data
     * @return List of parsed transactions
     * @throws TransactionReadException if reading fails
     */
    suspend fun read(inputStream: InputStream): List<Transaction>
}

/**
 * Exception thrown when transaction reading fails.
 */
class TransactionReadException(message: String, cause: Throwable? = null) : Exception(message, cause)
