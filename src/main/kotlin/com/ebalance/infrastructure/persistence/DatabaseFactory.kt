package com.ebalance.infrastructure.persistence

import org.flywaydb.core.Flyway
import org.sqlite.SQLiteDataSource
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
     * Runs Flyway migrations on the database.
     * @param dbPath Path to the SQLite database file
     */
    fun runMigrations(dbPath: String = DEFAULT_DB_NAME) {
        val flyway = Flyway.configure()
            .dataSource("jdbc:sqlite:$dbPath", null, null)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        
        flyway.migrate()
    }
}
