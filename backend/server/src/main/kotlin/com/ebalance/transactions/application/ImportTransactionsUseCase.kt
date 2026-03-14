package com.ebalance.transactions.application

import com.ebalance.classification.NeuralNetworkClassifier
import com.ebalance.transactions.infrastructure.excel.ExcelTransactionReader
import com.ebalance.transactions.infrastructure.persistence.CategoryTable
import com.ebalance.transactions.infrastructure.persistence.TransactionsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream

class ImportTransactionsUseCase(
    private val database: Database,
    private val modelPath: String
) {

    data class Result(
        val totalRead: Int,
        val inserted: Int,
        val duplicates: Int,
        val classified: Int
    )

    sealed class Stage {
        object Reading : Stage()
        data class Classifying(val total: Int) : Stage()
        object Saving : Stage()
        data class Complete(val result: Result) : Stage()
        data class Failed(val message: String) : Stage()
    }

    suspend fun execute(
        inputStream: InputStream,
        onStage: suspend (Stage) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        onStage(Stage.Reading)

        val transactions = ExcelTransactionReader().read(inputStream)

        // Load category enum_name → id mapping from DB
        val categoryMap: Map<String, Long> = transaction(database) {
            CategoryTable.selectAll().associate { row ->
                row[CategoryTable.enumName] to row[CategoryTable.id]
            }
        }

        onStage(Stage.Classifying(transactions.size))

        val classifier = NeuralNetworkClassifier(modelPath)
        classifier.load()
        val modelLoaded = classifier.isModelLoaded()

        var classified = 0
        val processed = if (modelLoaded) {
            classified = transactions.size
            transactions.map { tx ->
                val (label, _) = classifier.predict(tx.description)
                val catId = categoryMap[label] ?: 0L
                tx to catId
            }
        } else {
            transactions.map { tx -> tx to 0L }
        }

        onStage(Stage.Saving)

        var inserted = 0
        transaction(database) {
            for ((tx, catId) in processed) {
                inserted += TransactionsTable.insertIgnore {
                    it[operatedAt] = tx.operatedAt.toString()
                    it[description] = tx.description
                    it[value]       = tx.value
                    it[balance]     = tx.balance
                    it[categoryId]  = catId
                }.insertedCount
            }
        }

        val result = Result(
            totalRead  = transactions.size,
            inserted   = inserted,
            duplicates = transactions.size - inserted,
            classified = classified
        )
        onStage(Stage.Complete(result))
        result
    }
}
