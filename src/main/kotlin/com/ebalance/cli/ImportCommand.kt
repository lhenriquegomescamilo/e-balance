package com.ebalance.cli

import com.ebalance.cli.InitCommand.Dependencies
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream

class ImportCommand : CliktCommand(name = "import") {

    private val inputFile: Path by argument()
        .path(mustExist = true, mustBeReadable = true)

    private val dependencies: Dependencies by requireObject()

    override fun help(context: Context): String = 
        "Import transactions from an Excel file.\n\nArgument: Path to the Excel file (.xls format)"

    override fun run() {
        val importTransactionsUseCase = dependencies.importTransactionsUseCase

        // Validate file extension
        if (inputFile.extension.lowercase() !in listOf("xls", "xlsx")) {
            echo("Error: File must be an Excel file (.xls or .xlsx)", err = true)
            return
        }

        echo("Importing transactions from: $inputFile")

        runBlocking {
            try {
                inputFile.inputStream().use { inputStream ->
                    val result = importTransactionsUseCase.execute(inputStream)
                    
                    echo("""
                        |Import completed:
                        |  Total read:      ${result.totalRead}
                        |  Inserted:        ${result.totalInserted}
                        |  Duplicates:      ${result.duplicatesSkipped}
                    """.trimMargin())
                }
            } catch (e: Exception) {
                echo("Error during import: ${e.message}", err = true)
            }
        }
    }
}