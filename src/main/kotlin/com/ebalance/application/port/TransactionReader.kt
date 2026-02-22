package com.ebalance.application.port

import arrow.core.Either
import com.ebalance.domain.error.TransactionReadError
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
     * @return Either<TransactionReadError, List<Transaction>>
     */
    suspend fun read(inputStream: InputStream): Either<TransactionReadError, List<Transaction>>
}