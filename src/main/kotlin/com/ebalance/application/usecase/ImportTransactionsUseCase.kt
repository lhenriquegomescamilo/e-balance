package com.ebalance.application.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.application.port.TransactionReader
import com.ebalance.application.port.TransactionRepository
import com.ebalance.domain.error.ImportError
import com.ebalance.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.slf4j.LoggerFactory

/**
 * Use case for importing transactions from an external source into the repository.
 * Orchestrates reading, classification, and persistence operations.
 */
class ImportTransactionsUseCase(
    private val transactionReader: TransactionReader,
    private val transactionRepository: TransactionRepository,
    private val classifier: CategoryClassifierPort,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

    private val log = LoggerFactory.getLogger(ImportTransactionsUseCase::class.java)

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

            // Empty transaction list is valid - return zero counts
            if (transactions.isEmpty()) {
                return@either Result(
                    totalRead = 0,
                    totalInserted = 0,
                    duplicatesSkipped = 0,
                    classifiedCount = 0
                )
            }

            // Process transactions in chunks: classify (if available) then save
            val chunkSize = 500
            var totalInserted = 0
            var totalDuplicates = 0
            var classifiedCount = 0

            transactions.chunked(chunkSize).forEach { chunk ->
                val processedChunk = if (classifier.isModelLoaded()) {
                    classifiedCount += chunk.size
                    chunk.map { transaction ->
                        async(Dispatchers.IO) {
                            val classificationResult = classifier.classify(transaction.description)
                            val fromId = Category.fromId(classificationResult.categoryId)
                            log.info("Classified transaction: $fromId -> \tConfidence ${classificationResult.confidence} -> \t${transaction.description}")
                            transaction.copy(categoryId = classificationResult.categoryId)
                        }
                    }.awaitAll()
                } else {
                    log.warn("Classification model not loaded, skipping classification")
                    chunk
                }

                val saveResult = transactionRepository.saveAll(processedChunk)
                    .mapLeft { ImportError.PersistenceError(it) }
                    .bind()

                totalInserted += saveResult.inserted
                totalDuplicates += saveResult.duplicates
            }

            Result(
                totalRead = transactions.size,
                totalInserted = totalInserted,
                duplicatesSkipped = totalDuplicates,
                classifiedCount = classifiedCount
            )
        }
    }
}
