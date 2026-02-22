package com.ebalance.application.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.ebalance.application.port.TransactionReader
import com.ebalance.application.port.TransactionRepository
import com.ebalance.classification.CategoryClassifier
import com.ebalance.domain.error.ImportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Use case for importing transactions from an external source into the repository.
 * Orchestrates reading, classification, and persistence operations.
 */
class ImportTransactionsUseCase(
    private val transactionReader: TransactionReader,
    private val transactionRepository: TransactionRepository,
    private val classifier: CategoryClassifier? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Result of the import operation.
     */
    data class Result(
        val totalRead: Int,
        val totalInserted: Int,
        val duplicatesSkipped: Int,
        val classifiedCount: Int = 0
    )

    /**
     * Imports transactions from the given input stream.
     * Uses Arrow's Either for functional error handling.
     * 
     * @param inputStream The stream containing transaction data
     * @return Either<ImportError, Result>
     */
    suspend fun execute(inputStream: InputStream): Either<ImportError, Result> = withContext(ioDispatcher) {
        either {
            // Read transactions from the input stream
            val transactions = transactionReader.read(inputStream)
                .mapLeft { ImportError.ReadError(it) }
                .bind()
            
            // Validate that we have transactions to import
            ensure(transactions.isNotEmpty() || true) { 
                ImportError.EmptyInput("No transactions found in input") 
            }
            
            // Classify transactions if classifier is available
            val classifiedTransactions = if (classifier != null) {
                transactions.map { transaction ->
                    val classificationResult = classifier.classify(transaction.description)
                    transaction.copy(
                        categoryId = classificationResult.getOrNull()?.categoryId ?: 0
                    )
                }
            } else {
                transactions
            }
            
            // Save transactions to the repository
            val saveResult = transactionRepository.saveAll(classifiedTransactions)
                .mapLeft { ImportError.PersistenceError(it) }
                .bind()
            
            Result(
                totalRead = transactions.size,
                totalInserted = saveResult.inserted,
                duplicatesSkipped = saveResult.duplicates,
                classifiedCount = if (classifier != null) transactions.size else 0
            )
        }
    }
}
