package com.ebalance.classification

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Service for classifying transactions into categories.
 * Wraps the TextClassifier with Arrow Either for functional error handling.
 */
class CategoryClassifier(
    private val modelPath: String = "model.zip",
    private val datasetPath: String = "classpath:dataset/category.for.training.csv",
    private val labelToId: (String) -> Long = { label -> label.toLongOrNull() ?: 0L }
) {
    private val log = LoggerFactory.getLogger(CategoryClassifier::class.java)
    
    private val textClassifier: TextClassifier = TextClassifier(modelPath = modelPath)
    
    private val businessStopWords = setOf(
        "lda", "l d a", "Lda", "Unipessoal", "unipessoal", "sa", "s.a.", "s a", "limitada", "sociedade",
        "portugal", "portuguesa", "e", "de", "da", "do", "das", "dos", "com",
        "comunicacoes", "actividades", "gestao", "administracao", "servicos"
    )

    /**
     * Result of classification.
     */
    data class ClassificationResult(
        val categoryId: Long,
        val confidence: Double
    )

    /**
     * Loads the trained model from disk.
     * @return Either<ClassificationError, Unit>
     */
    fun load(): Either<ClassificationError, Unit> {
        return try {
            textClassifier.load()
            Unit.right()
        } catch (e: Exception) {
            ClassificationError.ModelLoadErr(e.message ?: "Unknown error loading model").left()
        }
    }

    /**
     * Checks if the model is loaded.
     */
    fun isModelLoaded(): Boolean = textClassifier.isModelLoaded()

    /**
     * Classifies a transaction description into a category.
     * @param description The transaction description to classify
     * @return Either<ClassificationError, ClassificationResult>
     */
    fun classify(description: String): Either<ClassificationError, ClassificationResult> {
        // Ensure model is loaded
        if (!isModelLoaded()) {
            load()
        }
        
        if (!isModelLoaded()) {
            return ClassificationError.ModelNotLoadedErr("Model not loaded - run training first").left()
        }

        return try {
            val (label, confidence) = textClassifier.predictWithScore(
                text = description,
                unknown = "DESCONHECIDA" to 0.0,
                aiThreshold = 0.70
            )
            
            // Convert label to category ID using the provided function
            val categoryId = labelToId(label)
            
            ClassificationResult(
                categoryId = categoryId,
                confidence = confidence
            ).right()
        } catch (e: Exception) {
            ClassificationError.ClassificationErr(e.message ?: "Unknown classification error").left()
        }
    }

    /**
     * Classifies multiple transaction descriptions.
     * @param descriptions The transaction descriptions to classify
     * @return Either<ClassificationError, List<ClassificationResult>>
     */
    fun classifyAll(descriptions: List<String>): Either<ClassificationError, List<ClassificationResult>> {
        return try {
            val results = descriptions.mapNotNull { desc ->
                classify(desc).getOrNull()
            }
            results.right()
        } catch (e: Exception) {
            ClassificationError.ClassificationErr(e.message ?: "Unknown error").left()
        }
    }

    /**
     * Training errors.
     */
    sealed class ClassificationError {
        data class ModelLoadErr(val msg: String) : ClassificationError()
        data class ModelNotLoadedErr(val msg: String) : ClassificationError()
        data class ClassificationErr(val msg: String) : ClassificationError()
        data class TrainingErr(val msg: String) : ClassificationError()
    }
}
