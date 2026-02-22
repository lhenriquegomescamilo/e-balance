package com.ebalance.infrastructure.persistence

import org.flywaydb.core.Flyway
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Factory for creating SQLite database connections and managing migrations.
 */
object DatabaseFactory {
    
    private const val DEFAULT_DB_NAME = "e-balance.db"
    
    /**
     * Creates a SQLite DataSource connected to the specified database file.
     * @param dbPath Path to the SQLite database file (defaults to current directory)
     * @return Configured SQLite DataSource
     */
    fun createDataSource(dbPath: String = DEFAULT_DB_NAME): DataSource {
        return SQLiteDataSource().apply {
            url = "jdbc:sqlite:$dbPath"
        }
    }
    
    /**
     * Ensures the database file exists and runs Flyway migrations.
     * @param dbPath Path to the SQLite database file
     */
    fun initialize(dbPath: String = DEFAULT_DB_NAME) {
        // Ensure database file exists
        ensureDatabaseExists(dbPath)
        
        // Run migrations
        runMigrations(dbPath)
    }
    
    /**
     * Ensures the SQLite database file exists. SQLite will create the file
     * automatically on first connection, but we do it explicitly for clarity.
     * @param dbPath Path to the SQLite database file
     */
    private fun ensureDatabaseExists(dbPath: String) {
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            // Create parent directories if needed
            dbFile.parentFile?.mkdirs()
            
            // Connect to create the database file
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                // Just connecting creates the file
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT 1")
                }
            }
        }
    }
    
    /**
     * Runs Flyway migrations on the database.
     * @param dbPath Path to the SQLite database file
     */
    private fun runMigrations(dbPath: String) {
        val flyway = Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        
        flyway.migrate()
    }
}
