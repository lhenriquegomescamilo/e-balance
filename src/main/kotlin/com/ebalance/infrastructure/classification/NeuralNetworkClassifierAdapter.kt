package com.ebalance.infrastructure.classification

import com.ebalance.application.port.CategoryClassifierPort
import com.ebalance.classification.NeuralNetworkClassifier
import com.ebalance.domain.model.Category
import org.slf4j.LoggerFactory

/**
 * Adapter that wraps [NeuralNetworkClassifier] to implement [CategoryClassifierPort].
 *
 * The neural network predicts category enum names (e.g. "HOSPITAL", "TRANSPORTE").
 * Those names are resolved to numeric IDs via [Category.fromEnumName].
 *
 * Train the model with:
 *   `./e-balance --model nn-model.bin train --engine neural-network`
 *
 * @param modelPath Path to the binary model file produced by [NeuralNetworkClassifier].
 */
class NeuralNetworkClassifierAdapter(
    private val modelPath: String = "nn-model.bin"
) : CategoryClassifierPort {

    private val log = LoggerFactory.getLogger(NeuralNetworkClassifierAdapter::class.java)

    private val classifier = NeuralNetworkClassifier(modelPath = modelPath)

    override fun isModelLoaded(): Boolean = classifier.isModelLoaded()

    override fun loadModel(): Boolean {
        log.debug("Loading neural network model from: $modelPath")
        classifier.load()
        val loaded = classifier.isModelLoaded()
        if (loaded) log.info("Neural network model loaded from $modelPath")
        else log.warn("Neural network model not found at $modelPath — run 'train --engine neural-network' first")
        return loaded
    }

    override fun classify(description: String): CategoryClassifierPort.ClassificationResult {
        val (label, confidence) = classifier.predict(description)
        val categoryId = Category.fromEnumName(label).id
        log.debug("NN classified '{}' → {} (id={}, confidence={})", description, label, categoryId, confidence)
        return CategoryClassifierPort.ClassificationResult(
            categoryId = categoryId,
            confidence = confidence
        )
    }

    override fun classifyAll(descriptions: List<String>): List<CategoryClassifierPort.ClassificationResult> {
        log.info("Batch classifying ${descriptions.size} descriptions with neural network")
        return descriptions.map { classify(it) }
    }
}
