package com.ebalance.cli

import arrow.core.fold
import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.application.usecase.ImportTransactionsUseCase
import com.ebalance.cli.InitCommand.Dependencies
import com.ebalance.domain.error.ImportError
import com.ebalance.domain.error.TransactionReadError
import com.ebalance.domain.error.TransactionRepositoryError
import com.ebalance.domain.model.Category
import com.ebalance.infrastructure.classification.CategoryClassifierAdapter
import com.ebalance.infrastructure.classification.NeuralNetworkClassifierAdapter
import com.ebalance.infrastructure.excel.ExcelTransactionReader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream

class ImportCommand : CliktCommand(name = "import") {

    private val inputFile: Path by argument()
        .path(mustExist = true, mustBeReadable = true)

    private val engine: String by option("--engine")
        .default(ClassifierEngine.PARAGRAPH_VECTORS.cliName)
        .help(
            "Classifier engine to use.\n" +
            "  paragraph-vectors  DL4J ParagraphVectors model (default, model file: *.zip)\n" +
            "  neural-network     Built-in bag-of-words neural network (model file: *.bin)"
        )

    private val dependencies: Dependencies by requireObject()

    override fun help(context: Context): String =
        "Import transactions from an Excel file.\n\nArgument: Path to the Excel file (.xls format)"

    override fun run() {
        if (inputFile.extension.lowercase() !in listOf("xls", "xlsx")) {
            echo("Error: File must be an Excel file (.xls or .xlsx)", err = true)
            return
        }

        val selectedEngine = ClassifierEngine.fromCli(engine) ?: run {
            echo("Error: Unknown engine '$engine'. Valid values: ${ClassifierEngine.cliNames()}", err = true)
            return
        }

        val classifierImpl = when (selectedEngine) {
            ClassifierEngine.NEURAL_NETWORK    -> "TextClassifierNeuralNetwork"
            ClassifierEngine.PARAGRAPH_VECTORS -> "TextClassifier (ParagraphVectors)"
        }

        echo("Importing transactions from: $inputFile")
        echo("Classifier engine:           ${selectedEngine.cliName}")
        echo("Classifier implementation:   $classifierImpl")

        // Build the classifier for the selected engine
        val labelToId: (String) -> Long = { label -> Category.fromEnumName(label).id }
        val classifier: CategoryClassifierPort = when (selectedEngine) {
            ClassifierEngine.NEURAL_NETWORK ->
                NeuralNetworkClassifierAdapter(modelPath = dependencies.modelPath)
            ClassifierEngine.PARAGRAPH_VECTORS ->
                CategoryClassifierAdapter(modelPath = dependencies.modelPath, labelToId = labelToId)
        }

        val modelLoaded = classifier.loadModel()
        if (modelLoaded) {
            echo("Classifier model loaded:     ${dependencies.modelPath}")
        } else {
            echo("Note: No classifier model found at ${dependencies.modelPath}")
            echo("      Run 'train --engine ${selectedEngine.cliName}' to train it first")
        }

        val importTransactionsUseCase = ImportTransactionsUseCase(
            transactionReader = ExcelTransactionReader(Dispatchers.IO),
            transactionRepository = dependencies.transactionRepository,
            classifier = classifier,
            ioDispatcher = Dispatchers.IO
        )

        runBlocking {
            inputFile.inputStream().use { inputStream ->
                importTransactionsUseCase.execute(inputStream).fold(
                    ifLeft = { error -> handleError(error) },
                    ifRight = { success ->
                        val classificationInfo = if (success.classifiedCount > 0) {
                            "\n  Classified:     ${success.classifiedCount}"
                        } else ""
                        echo("""
                            |Import completed:
                            |  Total read:      ${success.totalRead}
                            |  Inserted:        ${success.totalInserted}
                            |  Duplicates:      ${success.duplicatesSkipped}$classificationInfo
                        """.trimMargin())
                    }
                )
            }
        }
    }

    private fun handleError(error: ImportError) {
        val message = when (error) {
            is ImportError.ReadError -> formatReadError(error.error)
            is ImportError.PersistenceError -> formatRepositoryError(error.error)
            is ImportError.EmptyInput -> "No transactions found in the input file"
        }
        echo("Error: $message", err = true)
    }

    private fun formatReadError(error: TransactionReadError): String = when (error) {
        is TransactionReadError.FileNotFound -> "File not found: ${error.path}"
        is TransactionReadError.InvalidFormat -> "Invalid file format: ${error.message}"
        is TransactionReadError.ParseError -> "Failed to parse row ${error.rowIndex}: ${error.message}"
        is TransactionReadError.EmptyFile -> "The file contains no transaction data"
    }

    private fun formatRepositoryError(error: TransactionRepositoryError): String = when (error) {
        is TransactionRepositoryError.ConnectionError -> "Database connection error: ${error.message}"
        is TransactionRepositoryError.InsertError -> "Failed to save transactions: ${error.message}"
        is TransactionRepositoryError.QueryError -> "Database query error: ${error.message}"
    }
}
