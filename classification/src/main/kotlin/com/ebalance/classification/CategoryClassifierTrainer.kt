package com.ebalance.classification

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Service for training the category classifier.
 */
class CategoryClassifierTrainer(
    private val modelPath: String = "model.zip",
    private val datasetPath: String = "dataset/category.for.training.csv"
) {
    private val log = LoggerFactory.getLogger(CategoryClassifierTrainer::class.java)
    
    private val businessStopWords = setOf(
        "lda", "l d a", "Lda", "Unipessoal", "unipessoal", "sa", "s.a.", "s a", "limitada", "sociedade",
        "portugal", "portuguesa", "e", "de", "da", "do", "das", "dos", "com",
        "comunicacoes", "actividades", "gestao", "administracao", "servicos"
    )

    /**
     * Trains the classifier from the dataset on classpath.
     * @return Either<TrainingError, TrainingResult>
     */
    fun train(): Either<TrainingError, TrainingResult> {
        return try {
            val inputStream = this::class.java.classLoader.getResourceAsStream(datasetPath)
                ?: throw IllegalArgumentException("Dataset not found on classpath: $datasetPath")
            
            trainFromStream(inputStream)
        } catch (e: Exception) {
            log.error("Training failed", e)
            TrainingError.TrainProcessErr(e.message ?: "Unknown training error").left()
        }
    }

    /**
     * Alternative train method that takes an InputStream directly.
     * Useful for testing or when the dataset is not on classpath.
     */
    fun trainFromStream(inputStream: InputStream): Either<TrainingError, TrainingResult> {
        return try {
            val maps: Map<String, String> = inputStream.bufferedReader().use { reader ->
                reader
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .associate { line ->
                        val (id, name) = line.split(';', limit = 2)
                        cleanBusinessName(name.trim()) to id.trim()
                    }
            }
            
            log.info("Training for dataset with ${maps.size} entries")
            
            val dataset = maps.toList()
            val textClassifier = TextClassifier(modelPath = modelPath)
            textClassifier.train(dataset)
            
            log.info("Classifier trained for dataset with ${maps.size} entries")
            
            TrainingResult(entries = maps.size).right()
        } catch (e: Exception) {
            log.error("Training failed", e)
            TrainingError.TrainProcessErr(e.message ?: "Unknown training error").left()
        }
    }

    private fun cleanBusinessName(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "") // Remove punctuation
            .split(" ")
            .filter { it !in businessStopWords && it.isNotBlank() }
            .joinToString(" ")
    }

    data class TrainingResult(val entries: Int)

    sealed class TrainingError {
        data class DatasetNotFoundErr(val msg: String) : TrainingError()
        data class InvalidDatasetFormatErr(val msg: String) : TrainingError()
        data class TrainProcessErr(val msg: String) : TrainingError()
    }
}
