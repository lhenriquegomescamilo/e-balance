package com.ebalance.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.ebalance.application.port.TransactionRepository
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import javax.sql.DataSource

class PostgreSQLTransactionRepository(
    private val dataSource: DataSource,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {

    private fun connection(): Either<TransactionRepositoryError.ConnectionError, Connection> =
        runCatching { dataSource.connection }
            .fold(
                onSuccess = { it.right() },
                onFailure = { e ->
                    TransactionRepositoryError.ConnectionError(
                        message = "Failed to connect to database: ${e.message}",
                        cause = e
                    ).left()
                }
            )

    override suspend fun save(transaction: Transaction): Either<TransactionRepositoryError, Boolean> =
        withContext(ioDispatcher) {
            either {
                val conn = connection().bind()

                conn.use {
                    val sql = """
                        INSERT INTO transactions (operated_at, description, value, balance, category_id)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (operated_at, description, value) DO NOTHING
                    """.trimIndent()

                    executeInsert(it, sql, transaction).bind()
                }
            }
        }

    override suspend fun saveAll(transactions: List<Transaction>): Either<TransactionRepositoryError, TransactionRepository.SaveResult> =
        withContext(ioDispatcher) {
            if (transactions.isEmpty()) {
                return@withContext TransactionRepository.SaveResult(0, 0).right()
            }

            either {
                val conn = connection().bind()

                conn.use {
                    executeBatchInsert(it, transactions).bind()
                }
            }
        }

    override suspend fun count(): Either<TransactionRepositoryError, Long> =
        withContext(ioDispatcher) {
            either {
                val conn = connection().bind()

                conn.use { executeCount(it).bind() }
            }
        }

    override suspend fun findAll(): Either<TransactionRepositoryError, List<Transaction>> =
        withContext(ioDispatcher) {
            either {
                val conn = connection().bind()

                conn.use { executeFindAll(it).bind() }
            }
        }

    // --- Helper functions using runCatching + fold ---

    private fun executeInsert(
        conn: Connection,
        sql: String,
        transaction: Transaction
    ): Either<TransactionRepositoryError.InsertError, Boolean> =
        runCatching {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, transaction.operatedAt.toString())
                stmt.setString(2, transaction.description)
                stmt.setBigDecimal(3, transaction.value)
                stmt.setBigDecimal(4, transaction.balance)
                stmt.setLong(5, transaction.categoryId)

                stmt.executeUpdate() > 0
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

    private fun executeBatchInsert(
        conn: Connection,
        transactions: List<Transaction>
    ): Either<TransactionRepositoryError.InsertError, TransactionRepository.SaveResult> =
        runCatching {
            conn.autoCommit = false

            val sql = """
                INSERT INTO transactions (operated_at, description, value, balance, category_id)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (operated_at, description, value) DO NOTHING
            """.trimIndent()

            val insertedCount = conn.prepareStatement(sql).use { stmt ->
                transactions.sumOf { transaction ->
                    stmt.setString(1, transaction.operatedAt.toString())
                    stmt.setString(2, transaction.description)
                    stmt.setBigDecimal(3, transaction.value)
                    stmt.setBigDecimal(4, transaction.balance)
                    stmt.setLong(5, transaction.categoryId)
                    stmt.executeUpdate()
                }
            }

            conn.commit()
            TransactionRepository.SaveResult(
                inserted = insertedCount,
                duplicates = transactions.size - insertedCount
            )
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e ->
                runCatching { conn.rollback() }
                TransactionRepositoryError.InsertError(
                    message = "Failed to batch insert transactions: ${e.message}",
                    cause = e
                ).left()
            }
        )

    private fun executeCount(conn: Connection): Either<TransactionRepositoryError.QueryError, Long> =
        runCatching {
            conn.prepareStatement("SELECT COUNT(*) FROM transactions").use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
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

    private fun executeFindAll(conn: Connection): Either<TransactionRepositoryError.QueryError, List<Transaction>> =
        runCatching {
            conn.prepareStatement(
                "SELECT operated_at, description, value, balance, category_id FROM transactions ORDER BY operated_at DESC"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs else null }
                        .map { resultSet ->
                            Transaction(
                                operatedAt = java.time.LocalDate.parse(resultSet.getString("operated_at")),
                                description = resultSet.getString("description"),
                                value = resultSet.getBigDecimal("value"),
                                balance = resultSet.getBigDecimal("balance"),
                                categoryId = resultSet.getLong("category_id")
                            )
                        }
                        .toList()
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
