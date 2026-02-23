package com.ebalance.classification

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * Service for classifying transactions into categories.
 * Wraps the TextClassifier with Arrow Either for functional error handling.
 */
class CategoryClassifier(
    private val modelPath: String = "model.zip",
    private val labelToId: (String) -> Long = { label -> label.toLongOrNull() ?: 0L }
) {
    private val log = LoggerFactory.getLogger(CategoryClassifier::class.java)

    private val textClassifier: TextClassifier = TextClassifier(modelPath = modelPath)

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
    fun load(): Either<ClassificationError, Unit> = either {
        log.debug("Loading model from: $modelPath")
        val load = runCatching { textClassifier.load() }
            .map { textClassifier.isModelLoaded() }
            .onSuccess { log.info("Model loaded successfully") }
            .onFailure { log.warn("Model failed to load") }

        ensure(load.isSuccess && load.getOrNull() == true) { ClassificationError.ModelLoadErr("Model failed to load") }
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
        log.debug("Classifying: '$description'")

        // Ensure model is loaded
        if (!isModelLoaded()) {
            log.debug("Model not loaded, attempting to load...")
            load()
        }

        if (!isModelLoaded()) {
            log.error("Model not loaded - cannot classify")
            return ClassificationError.ModelNotLoadedErr("Model not loaded - run training first").left()
        }

        return try {
            val (label, confidence) = textClassifier.predictWithScore(
                text = description,
                unknown = "DESCONHECIDA" to 0.0,
                aiThreshold = 0.50
            )

            // Convert label to category ID using the provided function
            val categoryId = labelToId(label)

            ClassificationResult(
                categoryId = categoryId,
                confidence = confidence
            ).right()
        } catch (e: Exception) {
            log.error("Classification error for '$description': ${e.message}", e)
            ClassificationError.ClassificationErr(e.message ?: "Unknown classification error").left()
        }
    }

    /**
     * Classifies multiple transaction descriptions.
     * @param descriptions The transaction descriptions to classify
     * @return Either<ClassificationError, List<ClassificationResult>>
     */
    fun classifyAll(input: List<String>): Either<ClassificationError, List<ClassificationResult>> {
        return try {
            val output = ArrayList<ClassificationResult>(input.size)
            val chuncked = input.chunked(100)
            runBlocking(Dispatchers.IO) {
                val results = ArrayList<Deferred<List<ClassificationResult>>>(chuncked.size)
                for (descriptions in chuncked) {
                    val deferred =
                        async(Dispatchers.IO) { descriptions.mapNotNull { desc -> classify(desc).getOrNull() } }
                    results.add(deferred)
                }
                results.awaitAll().flatten().forEach { output.add(it) }

            }
            output.right()
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
