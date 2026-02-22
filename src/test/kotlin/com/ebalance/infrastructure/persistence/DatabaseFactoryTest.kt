package com.ebalance.infrastructure.persistence

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.flywaydb.core.Flyway
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager

class DatabaseFactoryTest : DescribeSpec({
    
    describe("DatabaseFactory") {
        
        lateinit var tempDbFile: File
        
        beforeEach {
            tempDbFile = Files.createTempFile("test-db", ".db").toFile()
            tempDbFile.delete()
        }
        
        afterEach {
            if (tempDbFile.exists()) {
                tempDbFile.delete()
            }
        }
        
        describe("initialize") {
            
            it("should create database file if it does not exist") {
                tempDbFile.exists() shouldBe false
                
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                
                tempDbFile.exists() shouldBe true
            }
            
            it("should run Flyway migrations") {
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                
                // Verify migrations ran by checking flyway_schema_history
                DriverManager.getConnection("jdbc:sqlite:${tempDbFile.absolutePath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM flyway_schema_history"
                        )
                        rs.next()
                        rs.getInt(1) shouldBe 2 // Two migrations should have run (V1 and V2)
                    }
                }
            }
            
            it("should create transactions table") {
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                
                DriverManager.getConnection("jdbc:sqlite:${tempDbFile.absolutePath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='transactions'"
                        )
                        rs.next() shouldBe true
                        rs.getString("name") shouldBe "transactions"
                    }
                }
            }
            
            it("should create unique index on transactions") {
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                
                DriverManager.getConnection("jdbc:sqlite:${tempDbFile.absolutePath}").use { conn ->
                    conn.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_transactions_unique'"
                        )
                        rs.next() shouldBe true
                        rs.getString("name") shouldBe "idx_transactions_unique"
                    }
                }
            }
            
            it("should be idempotent - calling initialize multiple times should not fail") {
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                DatabaseFactory.initialize(tempDbFile.absolutePath)
                
                tempDbFile.exists() shouldBe true
            }
            
            it("should work with nested directory paths") {
                val nestedDir = Files.createTempDirectory("nested").toFile()
                val nestedDbFile = File(nestedDir, "subdir/database.db")
                
                DatabaseFactory.initialize(nestedDbFile.absolutePath)
                
                nestedDbFile.exists() shouldBe true
                nestedDbFile.parentFile.deleteRecursively()
            }
        }
        
        describe("createDataSource") {
            
            it("should create a valid SQLite data source") {
                val dataSource = DatabaseFactory.createDataSource(tempDbFile.absolutePath)
                
                dataSource.connection.use { conn ->
                    conn.isValid(1) shouldBe true
                }
            }
            
            it("should create data source with correct URL") {
                val dataSource = DatabaseFactory.createDataSource(tempDbFile.absolutePath)
                
                dataSource.connection.use { conn ->
                    conn.metaData.url shouldContain "sqlite"
                }
            }
        }
    }
})