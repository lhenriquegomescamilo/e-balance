package com.ebalance.application.port

/**
 * Port interface for transaction classification.
 * This allows the application layer to depend on an abstraction rather than
 * a concrete implementation (CategoryClassifier in infrastructure).
 */
interface CategoryClassifierPort {
    
    /**
     * Result of classification.
     */
    data class ClassificationResult(
        val categoryId: Long,
        val confidence: Double
    )

    /**
     * Checks if the classifier model is loaded.
     */
    fun isModelLoaded(): Boolean

    /**
     * Loads the trained model from disk.
     * @return true if model was loaded successfully
     */
    fun loadModel(): Boolean

    /**
     * Classifies a transaction description into a category.
     * @param description The transaction description to classify
     * @return ClassificationResult with category ID and confidence
     */
    fun classify(description: String): ClassificationResult

    /**
     * Classifies multiple transaction descriptions.
     * @param descriptions The transaction descriptions to classify
     * @return List of ClassificationResults
     */
    fun classifyAll(descriptions: List<String>): List<ClassificationResult>
}
