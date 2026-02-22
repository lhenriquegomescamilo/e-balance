package com.ebalance.infrastructure.classification

import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.classification.CategoryClassifier

/**
 * Adapter that wraps CategoryClassifier to implement the CategoryClassifierPort interface.
 * This follows Clean Architecture by adapting the infrastructure implementation to the port.
 */
class CategoryClassifierAdapter(
    private val modelPath: String = "model.zip",
    private val labelToId: (String) -> Long = { label -> label.toLongOrNull() ?: 0L }
) : CategoryClassifierPort {

    private val classifier: CategoryClassifier = CategoryClassifier(
        modelPath = modelPath,
        labelToId = labelToId
    )

    override fun isModelLoaded(): Boolean = classifier.isModelLoaded()

    override fun loadModel(): Boolean {
        return classifier.load().isRight()
    }

    override fun classify(description: String): CategoryClassifierPort.ClassificationResult {
        val result = classifier.classify(description)
        return if (result.isRight()) {
            result.getOrNull()!!.let {
                CategoryClassifierPort.ClassificationResult(
                    categoryId = it.categoryId,
                    confidence = it.confidence
                )
            }
        } else {
            // Return default unknown category on error
            CategoryClassifierPort.ClassificationResult(
                categoryId = 0L,
                confidence = 0.0
            )
        }
    }

    override fun classifyAll(descriptions: List<String>): List<CategoryClassifierPort.ClassificationResult> {
        return descriptions.map { classify(it) }
    }
}
