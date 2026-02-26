package com.ebalance.cli

import com.ebalance.application.port.TransactionRepository
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

    val modelPath: String by option("--model", "-m")
        .default("model.zip")
        .help("Path to the trained classifier model file")

    override fun help(context: Context): String = "E-Balance: Personal finance transaction manager"

    override fun run() {
        echo("Initializing database: $dbPath")
        DatabaseFactory.initialize(dbPath)

        val transactionRepository: TransactionRepository = SQLiteTransactionRepository(dbPath, Dispatchers.IO)

        // Classifier and use-case creation is left to each subcommand so they can
        // independently choose their --engine.
        currentContext.obj = Dependencies(
            transactionRepository = transactionRepository,
            modelPath = modelPath
        )
    }

    /**
     * Shared infrastructure made available to all subcommands via the Clikt context object.
     *
     * Classifier selection is intentionally deferred to each subcommand (see [ImportCommand],
     * [TrainCommand]) so that `--engine` can be a per-subcommand option.
     */
    data class Dependencies(
        val transactionRepository: TransactionRepository,
        val modelPath: String
    )
}
