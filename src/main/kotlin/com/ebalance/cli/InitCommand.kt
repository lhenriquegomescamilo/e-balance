package com.ebalance.cli

import com.ebalance.application.port.TransactionRepository
import com.ebalance.application.usecase.ImportTransactionsUseCase
import com.ebalance.infrastructure.excel.ExcelTransactionReader
import com.ebalance.infrastructure.persistence.DatabaseFactory
import com.ebalance.infrastructure.persistence.SQLiteTransactionRepository
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.Dispatchers

class InitCommand : CliktCommand() {

    private val dbPath: String by option("--db", "-d")
        .default("e-balance.db")
        .help("Path to SQLite database file")

    override fun help(context: Context): String = "E-Balance: Personal finance transaction manager"

    override fun run() {
        // Run database migrations at startup
        echo("Initializing database: $dbPath")
        DatabaseFactory.runMigrations(dbPath)
        
        // Wire up dependencies
        val transactionReader = ExcelTransactionReader(Dispatchers.IO)
        val transactionRepository: TransactionRepository = SQLiteTransactionRepository(dbPath, Dispatchers.IO)
        val importUseCase = ImportTransactionsUseCase(transactionReader, transactionRepository, Dispatchers.IO)
        
        // Make dependencies available to subcommands via context
        currentContext.obj = Dependencies(importUseCase, transactionRepository)
    }
    
    /**
     * Container for shared dependencies passed to subcommands.
     */
    data class Dependencies(
        val importTransactionsUseCase: ImportTransactionsUseCase,
        val transactionRepository: TransactionRepository
    )
}
