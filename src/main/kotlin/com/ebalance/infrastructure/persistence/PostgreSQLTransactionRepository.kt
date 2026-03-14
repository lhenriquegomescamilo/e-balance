package com.ebalance.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ebalance.application.port.TransactionRepository
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.domain.model.Transaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate

class PostgreSQLTransactionRepository(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {

    override suspend fun save(tx: Transaction): Either<TransactionRepositoryError, Boolean> =
        withContext(ioDispatcher) {
            runCatching {
                transaction(database) {
                    val result = TransactionsTable.insertIgnore {
                        it[operatedAt] = tx.operatedAt.toString()
                        it[description] = tx.description
                        it[value] = tx.value.toDouble()
                        it[balance] = tx.balance.toDouble()
                        it[categoryId] = tx.categoryId.toInt()
                    }
                    result.insertedCount > 0
                }
            }.fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionRepositoryError.InsertError(
                        message = "Failed to insert transaction: ${e.message}",
                        cause = e
                    ).left()
                }
            )
        }

    override suspend fun saveAll(transactions: List<Transaction>): Either<TransactionRepositoryError, TransactionRepository.SaveResult> =
        withContext(ioDispatcher) {
            if (transactions.isEmpty()) {
                return@withContext TransactionRepository.SaveResult(0, 0).right()
            }
            runCatching {
                transaction(database) {
                    val inserted = transactions.sumOf { tx ->
                        TransactionsTable.insertIgnore {
                            it[operatedAt] = tx.operatedAt.toString()
                            it[description] = tx.description
                            it[value] = tx.value.toDouble()
                            it[balance] = tx.balance.toDouble()
                            it[categoryId] = tx.categoryId.toInt()
                        }.insertedCount
                    }
                    TransactionRepository.SaveResult(
                        inserted = inserted,
                        duplicates = transactions.size - inserted
                    )
                }
            }.fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionRepositoryError.InsertError(
                        message = "Failed to batch insert transactions: ${e.message}",
                        cause = e
                    ).left()
                }
            )
        }

    override suspend fun count(): Either<TransactionRepositoryError, Long> =
        withContext(ioDispatcher) {
            runCatching {
                transaction(database) {
                    TransactionsTable.selectAll().count()
                }
            }.fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionRepositoryError.QueryError(
                        message = "Failed to count transactions: ${e.message}",
                        cause = e
                    ).left()
                }
            )
        }

    override suspend fun findAll(): Either<TransactionRepositoryError, List<Transaction>> =
        withContext(ioDispatcher) {
            runCatching {
                transaction(database) {
                    TransactionsTable.selectAll()
                        .orderBy(TransactionsTable.operatedAt to SortOrder.DESC)
                        .map { row ->
                            Transaction(
                                operatedAt = LocalDate.parse(row[TransactionsTable.operatedAt]),
                                description = row[TransactionsTable.description],
                                value = BigDecimal.valueOf(row[TransactionsTable.value]),
                                balance = BigDecimal.valueOf(row[TransactionsTable.balance]),
                                categoryId = row[TransactionsTable.categoryId].toLong()
                            )
                        }
                }
            }.fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionRepositoryError.QueryError(
                        message = "Failed to query transactions: ${e.message}",
                        cause = e
                    ).left()
                }
            )
        }
}
