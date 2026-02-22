package com.ebalance.cli

import arrow.core.fold
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.infrastructure.google.GoogleSheetsExporter
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking

class ExportCommand : CliktCommand(name = "export") {

    private val spreadsheetId: String by argument()
    
    private val credentialsPath: String by option("--credentials", "-c")
        .default("credentials.json")
        .help("Path to Google API credentials JSON file")

    private val bankAccount: String by option("--bank-account", "-b")
        .default("Santander")
        .help("Bank account name")

    private val responsible: String by option("--responsible", "-r")
        .default("Luis Camilo")
        .help("Responsible person name")

    private val dependencies: InitCommand.Dependencies by requireObject()

    override fun help(context: Context): String = 
        "Export transactions to Google Sheets.\n\n" +
        "Argument: Google Sheets spreadsheet ID\n" +
        "  Get the spreadsheet ID from the URL:\n" +
        "  https://docs.google.com/spreadsheets/d/<THIS_PART>/edit\n\n" +
        "The spreadsheet should have the following columns:\n" +
        "A: Categoria | B: Valor | C: Conta Bancária | D: Responsável | E: Tipo | F: Observações | G: Data Pagamento"

    override fun run() {
        echo("Exporting transactions to Google Sheets...")
        
        // Get transactions from repository
        val repository = dependencies.transactionRepository
        
        runBlocking {
            val transactionsResult = repository.findAll()
            
            transactionsResult.fold(
                ifLeft = { error ->
                    val message = when (error) {
                        is TransactionRepositoryError.ConnectionError -> "Database connection error: ${error.message}"
                        is TransactionRepositoryError.InsertError -> "Insert error: ${error.message}"
                        is TransactionRepositoryError.QueryError -> "Query error: ${error.message}"
                    }
                    echo("Error: $message", err = true)
                },
                ifRight = { transactions ->
                    if (transactions.isEmpty()) {
                        echo("No transactions found in database")
                        return@runBlocking
                    }
                    
                    echo("Found ${transactions.size} transactions to export")
                    
                    val exporter = GoogleSheetsExporter(
                        credentialsPath = credentialsPath,
                        spreadsheetId = spreadsheetId
                    )
                    
                    val exportResult = exporter.export(
                        transactions = transactions,
                        bankAccount = bankAccount,
                        responsible = responsible
                    )
                    
                    exportResult.fold(
                        ifLeft = { error ->
                            val message = when (error) {
                                is GoogleSheetsExporter.GoogleSheetsError.AuthenticationError -> 
                                    "Authentication failed: ${error.message}"
                                is GoogleSheetsExporter.GoogleSheetsError.ExportError -> 
                                    "Export failed: ${error.message}"
                                is GoogleSheetsExporter.GoogleSheetsError.NoDataToExport -> 
                                    "No data to export: ${error.message}"
                            }
                            echo("Error: $message", err = true)
                        },
                        ifRight = { result ->
                            val sheetInfo = result.sheetResults.joinToString("\n") { sheet ->
                                "  - ${sheet.sheetName}: ${sheet.exported} transactions (rows ${sheet.startRow}-${sheet.endRow})"
                            }
                            echo("""
                                |Export completed successfully!
                                |  Total exported: ${result.exported} transactions
                                |
                                |Sheets:
                                |$sheetInfo
                            """.trimMargin())
                        }
                    )
                }
            )
        }
    }
}
