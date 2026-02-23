package com.ebalance.infrastructure.classification

import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.classification.CategoryClassifier
import java.util.function.Function.identity
import org.slf4j.LoggerFactory

/**
 * Adapter that wraps CategoryClassifier to implement the CategoryClassifierPort interface.
 * This follows Clean Architecture by adapting the infrastructure implementation to the port.
 */
class CategoryClassifierAdapter(
    private val modelPath: String = "model.zip",
    private val labelToId: (String) -> Long = { label -> label.toLongOrNull() ?: 0L }
) : CategoryClassifierPort {

    private val log = LoggerFactory.getLogger(CategoryClassifierAdapter::class.java)

    private val classifier: CategoryClassifier = CategoryClassifier(
        modelPath = modelPath,
        labelToId = labelToId
    )

    override fun isModelLoaded(): Boolean = classifier.isModelLoaded()

    override fun loadModel(): Boolean {
        log.debug("Loading classification model...")
        val result = classifier.load()
        return result.isRight()
    }

    override fun classify(description: String): CategoryClassifierPort.ClassificationResult {
        log.debug("Adapter classifying: '$description'")
        val result = classifier.classify(description)
        return result
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
    }

    override fun classifyAll(descriptions: List<String>): List<CategoryClassifierPort.ClassificationResult> {
        log.info("Batch classifying ${descriptions.size} descriptions")
        val results = descriptions.map { classify(it) }
        log.info("Batch classification completed: ${results.size} results")
        return results
    }
}
