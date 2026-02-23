package com.ebalance.infrastructure.classification

import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.classification.CategoryClassifier
import com.ebalance.domain.model.Category
import org.slf4j.LoggerFactory
import java.io.InputStream

/**
 * Adapter that wraps CategoryClassifier to implement the CategoryClassifierPort interface.
 * This follows Clean Architecture by adapting the infrastructure implementation to the port.
 * Includes opencode CLI fallback for DESCONHECIDA classifications.
 */
class CategoryClassifierAdapter(
    private val modelPath: String = "model.zip",
    private val labelToId: (String) -> Long = { label -> label.toLongOrNull() ?: 0L },
    private val opencodePath: String = "opencode",
    private val trainingDataStream: InputStream? = CategoryClassifierAdapter::class.java.getResourceAsStream("/dataset/category.for.training.csv")
) : CategoryClassifierPort {

    private val log = LoggerFactory.getLogger(CategoryClassifierAdapter::class.java)

    private val classifier: CategoryClassifier = CategoryClassifier(
        modelPath = modelPath,
        labelToId = labelToId
    )

    private val trainingData: String by lazy {
        trainingDataStream?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: ""
    }

    override fun isModelLoaded(): Boolean = classifier.isModelLoaded()

    override fun loadModel(): Boolean {
        log.debug("Loading classification model...")
        val result = classifier.load()
        return result.isRight()
    }

    override fun classify(description: String): CategoryClassifierPort.ClassificationResult {
        log.debug("Adapter classifying: '$description'")
        val result = classifier.classify(description)
        
        val classificationResult = result
            .map {
                CategoryClassifierPort.ClassificationResult(
                    categoryId = it.categoryId,
                    confidence = it.confidence
                )
            }
            .mapLeft {
                CategoryClassifierPort.ClassificationResult(
                    categoryId = 0L,
                    confidence = 0.0
                )
            }
            .onLeft { log.error("Classification failed for '$description': ${result.leftOrNull()}") }
            .onRight { log.debug("Adapter classification result: {}", it) }
            .fold({ it }, { it })

        // If classification returns DESCONHECIDA (category 0), try opencode CLI fallback
        return if (classificationResult.categoryId == 0L) {
            log.info("Model classified as DESCONHECIDA, trying opencode CLI fallback for: '$description'")
            classifyWithOpencode(description)
        } else {
            classificationResult
        }
    }

    private fun classifyWithOpencode(description: String): CategoryClassifierPort.ClassificationResult {
        return try {
            val categoriesList = Category.entries.joinToString(", ") { 
                "${it.id}=${it.displayName}"
            }

            val prompt = """
Classify this transaction: "$description"

Available categories (id=name): $categoriesList

Examples from training data:
$trainingData

Return ONLY the category ID number. If unsure, return 0.
            """.trimIndent().replace("\n", " ")

            val process = Runtime.getRuntime().exec(arrayOf(opencodePath, "--model", "big-pickle", prompt))
            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            log.debug("opencode output: $output, error: $errorOutput, exitCode: $exitCode")

            // Extract first number from output as category ID
            val categoryId = output.filter { it.isDigit() }.take(2).toLongOrNull() ?: 0L

            log.info("opencode classified '$description' as category $categoryId")
            CategoryClassifierPort.ClassificationResult(categoryId = categoryId, confidence = 0.9)
        } catch (e: Exception) {
            log.error("opencode classification failed for '$description': ${e.message}")
            CategoryClassifierPort.ClassificationResult(0L, 0.0)
        }
    }

    override fun classifyAll(descriptions: List<String>): List<CategoryClassifierPort.ClassificationResult> {
        log.info("Batch classifying ${descriptions.size} descriptions")
        val results = descriptions.map { classify(it) }
        log.info("Batch classification completed: ${results.size} results")
        return results
    }
}
