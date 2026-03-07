package com.ebalance.infrastructure.persistence

import arrow.core.fold
import com.ebalance.TestFixtures
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.LocalDate

class PostgreSQLTransactionRepositoryTest : DescribeSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("ebalance_test")
        .withUsername("ebalance")
        .withPassword("ebalance")

    lateinit var dataSource: HikariDataSource
    lateinit var repository: PostgreSQLTransactionRepository

    beforeSpec {
        postgres.start()

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
        })

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    afterSpec {
        dataSource.close()
        postgres.stop()
    }

    beforeEach {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE transactions RESTART IDENTITY")
            }
        }
        repository = PostgreSQLTransactionRepository(dataSource, Dispatchers.IO)
    }

    describe("PostgreSQLTransactionRepository") {

        describe("save") {

            it("should save a single transaction") {
                runBlocking {
                    val transaction = TestFixtures.aTransaction()

                    val result = repository.save(transaction)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { inserted -> inserted shouldBe true }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 1L }
                    )
                }
            }

            it("should return false when saving duplicate transaction") {
                runBlocking {
                    val transaction = TestFixtures.aTransaction()

                    repository.save(transaction)
                    val result = repository.save(transaction)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { inserted -> inserted shouldBe false }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 1L }
                    )
                }
            }

            it("should save multiple different transactions") {
                runBlocking {
                    val transaction1 = TestFixtures.aTransaction(description = "Transaction 1")
                    val transaction2 = TestFixtures.aTransaction(description = "Transaction 2")

                    repository.save(transaction1)
                    repository.save(transaction2)

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 2L }
                    )
                }
            }
        }

        describe("saveAll") {

            it("should save all transactions") {
                runBlocking {
                    val transactions = TestFixtures.aListOfTransactions(3)

                    val result = repository.saveAll(transactions)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { saveResult ->
                            saveResult.inserted shouldBe 3
                            saveResult.duplicates shouldBe 0
                        }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 3L }
                    )
                }
            }

            it("should skip duplicates when saving") {
                runBlocking {
                    val transactions = TestFixtures.aListOfTransactions(3)

                    repository.saveAll(transactions)
                    val result = repository.saveAll(transactions)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { saveResult ->
                            saveResult.inserted shouldBe 0
                            saveResult.duplicates shouldBe 3
                        }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 3L }
                    )
                }
            }

            it("should return 0 for empty list") {
                runBlocking {
                    val result = repository.saveAll(emptyList())

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { saveResult ->
                            saveResult.inserted shouldBe 0
                            saveResult.duplicates shouldBe 0
                        }
                    )
                }
            }
        }

        describe("count") {

            it("should return 0 for empty database") {
                runBlocking {
                    val count = repository.count()

                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 0L }
                    )
                }
            }
        }

        describe("findAll") {

            it("should return empty list for empty database") {
                runBlocking {
                    val result = repository.findAll()

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe emptyList() }
                    )
                }
            }

            it("should return transactions ordered by date descending") {
                runBlocking {
                    val transaction1 = TestFixtures.aTransaction(
                        operatedAt = LocalDate.of(2026, 2, 20),
                        description = "Older"
                    )
                    val transaction2 = TestFixtures.aTransaction(
                        operatedAt = LocalDate.of(2026, 2, 25),
                        description = "Newer"
                    )

                    repository.saveAll(listOf(transaction1, transaction2))

                    val result = repository.findAll()

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { transactions ->
                            transactions shouldHaveSize 2
                            transactions[0].description shouldBe "Newer"
                            transactions[1].description shouldBe "Older"
                        }
                    )
                }
            }
        }

        describe("duplicate detection") {

            it("should detect duplicates based on operated_at, description, and value") {
                runBlocking {
                    val transaction = TestFixtures.aTransaction(
                        operatedAt = LocalDate.of(2026, 2, 22),
                        description = "Same Transaction",
                        value = BigDecimal("-50.00")
                    )

                    repository.save(transaction)

                    val duplicate = transaction.copy(balance = BigDecimal("999.00"))
                    val result = repository.save(duplicate)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { inserted -> inserted shouldBe false }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 1L }
                    )
                }
            }

            it("should allow same transaction on different dates") {
                runBlocking {
                    val transaction1 = TestFixtures.aTransaction(
                        operatedAt = LocalDate.of(2026, 2, 20),
                        description = "Coffee Shop"
                    )
                    val transaction2 = TestFixtures.aTransaction(
                        operatedAt = LocalDate.of(2026, 2, 21),
                        description = "Coffee Shop"
                    )

                    repository.save(transaction1)
                    val result = repository.save(transaction2)

                    result.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { inserted -> inserted shouldBe true }
                    )

                    val count = repository.count()
                    count.fold(
                        ifLeft = { throw AssertionError("Expected Right but got Left: $it") },
                        ifRight = { it shouldBe 2L }
                    )
                }
            }
        }
    }
})
