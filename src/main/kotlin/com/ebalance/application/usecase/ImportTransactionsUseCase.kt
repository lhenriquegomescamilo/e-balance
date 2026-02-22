package com.ebalance.application.usecase

import com.ebalance.application.port.TransactionReader
import com.ebalance.application.port.TransactionRepository
import com.ebalance.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Use case for importing transactions from an external source into the repository.
 * Orchestrates reading and persistence operations.
 */
class ImportTransactionsUseCase(
    private val transactionReader: TransactionReader,
    private val transactionRepository: TransactionRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Result of the import operation.
     */
    data class Result(
        val totalRead: Int,
        val totalInserted: Int,
        val duplicatesSkipped: Int
    )

    /**
     * Imports transactions from the given input stream.
     * @param inputStream The stream containing transaction data
     * @return Result containing import statistics
     */
    suspend fun execute(inputStream: InputStream): Result = withContext(ioDispatcher) {
        val transactions = transactionReader.read(inputStream)
        val inserted = transactionRepository.saveAll(transactions)
        
        Result(
            totalRead = transactions.size,
            totalInserted = inserted,
            duplicatesSkipped = transactions.size - inserted
        )
    }
}
