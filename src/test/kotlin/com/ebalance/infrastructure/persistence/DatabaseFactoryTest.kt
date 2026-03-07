package com.ebalance.infrastructure.persistence

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.PostgreSQLContainer

class DatabaseFactoryTest : DescribeSpec({

    val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("ebalance_test")
        .withUsername("ebalance")
        .withPassword("ebalance")

    lateinit var dataSource: HikariDataSource

    beforeSpec {
        postgres.start()
        val config = DbConfig(
            url = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password
        )
        dataSource = DatabaseFactory.initialize(config) as HikariDataSource
    }

    afterSpec {
        dataSource.close()
        postgres.stop()
    }

    describe("DatabaseFactory") {

        describe("initialize") {

            it("should run Flyway migrations") {
                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")
                        rs.next()
                        rs.getInt(1) shouldBe 4 // V1, V2, V3, V4
                    }
                }
            }

            it("should create transactions table") {
                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            """
                            SELECT COUNT(*) FROM information_schema.tables
                            WHERE table_name = 'transactions'
                            """.trimIndent()
                        )
                        rs.next()
                        rs.getInt(1) shouldBe 1
                    }
                }
            }

            it("should be idempotent - calling initialize again should not fail") {
                val config = DbConfig(
                    url = postgres.jdbcUrl,
                    username = postgres.username,
                    password = postgres.password
                )
                val ds2 = DatabaseFactory.initialize(config) as HikariDataSource
                ds2.connection.use { conn -> conn.isValid(1) shouldBe true }
                ds2.close()
            }
        }

        describe("createDataSource") {

            it("should create a valid PostgreSQL DataSource") {
                dataSource.connection.use { conn ->
                    conn.isValid(1) shouldBe true
                }
            }

            it("should create DataSource with PostgreSQL URL") {
                dataSource.connection.use { conn ->
                    conn.metaData.url shouldContain "postgresql"
                }
            }
        }
    }
})
