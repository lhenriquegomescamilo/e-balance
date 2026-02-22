package com.ebalance.infrastructure.persistence

import com.ebalance.application.port.TransactionRepository
import com.ebalance.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * SQLite implementation of TransactionRepository.
 * Uses raw JDBC for simplicity with SQLite.
 */
class SQLiteTransactionRepository(
    private val dbPath: String,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {

    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    override suspend fun save(transaction: Transaction): Boolean = withContext(ioDispatcher) {
        connection().use { conn ->
            val sql = """
                INSERT OR IGNORE INTO transactions (operated_at, description, value, balance)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, transaction.operatedAt.toString())
                stmt.setString(2, transaction.description)
                stmt.setBigDecimal(3, transaction.value)
                stmt.setBigDecimal(4, transaction.balance)
                
                stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun saveAll(transactions: List<Transaction>): Int = withContext(ioDispatcher) {
        if (transactions.isEmpty()) return@withContext 0
        
        connection().use { conn ->
            conn.autoCommit = false
            
            try {
                val sql = """
                    INSERT OR IGNORE INTO transactions (operated_at, description, value, balance)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()
                
                var insertedCount = 0
                
                conn.prepareStatement(sql).use { stmt ->
                    for (transaction in transactions) {
                        stmt.setString(1, transaction.operatedAt.toString())
                        stmt.setString(2, transaction.description)
                        stmt.setBigDecimal(3, transaction.value)
                        stmt.setBigDecimal(4, transaction.balance)
                        insertedCount += stmt.executeUpdate()
                    }
                }
                
                conn.commit()
                insertedCount
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    override suspend fun count(): Long = withContext(ioDispatcher) {
        connection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM transactions").use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        }
    }

    override suspend fun findAll(): List<Transaction> = withContext(ioDispatcher) {
        connection().use { conn ->
            conn.prepareStatement(
                "SELECT operated_at, description, value, balance FROM transactions ORDER BY operated_at DESC"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val transactions = mutableListOf<Transaction>()
                    while (rs.next()) {
                        transactions.add(
                            Transaction(
                                operatedAt = java.time.LocalDate.parse(rs.getString("operated_at")),
                                description = rs.getString("description"),
                                value = rs.getBigDecimal("value"),
                                balance = rs.getBigDecimal("balance")
                            )
                        )
                    }
                    transactions
                }
            }
        }
    }
}
