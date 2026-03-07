package com.ebalance.cli

import com.ebalance.application.port.TransactionRepository
import com.ebalance.infrastructure.persistence.DatabaseFactory
import com.ebalance.infrastructure.persistence.DbConfig
import com.ebalance.infrastructure.persistence.PostgreSQLTransactionRepository
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.Properties

class InitCommand : CliktCommand() {

    private val configFile: String by option("--config", "-c")
        .default("db.properties")
        .help("Path to database config properties file (db.url, db.username, db.password)")

    val modelPath: String by option("--model", "-m")
        .default("model.zip")
        .help("Path to the trained classifier model file")

    override fun help(context: Context): String = "E-Balance: Personal finance transaction manager"

    override fun run() {
        val config = loadDbConfig(configFile)
        echo("Connecting to database: ${config.url}")
        val dataSource = DatabaseFactory.initialize(config)

        val transactionRepository: TransactionRepository =
            PostgreSQLTransactionRepository(dataSource, Dispatchers.IO)

        currentContext.obj = Dependencies(
            transactionRepository = transactionRepository,
            modelPath = modelPath
        )
    }

    private fun loadDbConfig(path: String): DbConfig {
        val props = Properties()
        val file = File(path)
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        }
        return DbConfig(
            url = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/ebalance"),
            username = props.getProperty("db.username", "ebalance"),
            password = props.getProperty("db.password", "ebalance")
        )
    }

    data class Dependencies(
        val transactionRepository: TransactionRepository,
        val modelPath: String
    )
}
