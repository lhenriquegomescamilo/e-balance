package com.ebalance.classification

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Adapter that wraps [TextClassifierNeuralNetwork] and provides a text-level interface:
 * training from raw "LABEL;Description" strings, model persistence, and text prediction.
 *
 * Model persistence is fully delegated to [TextClassifierNeuralNetwork.saveModel] and
 * [TextClassifierNeuralNetwork.loadModel], which own the binary format.
 *
 * @param modelPath  Path used by [save] and [load].
 * @param hiddenSize Number of hidden neurons for newly created networks.
 */
class NeuralNetworkClassifier(
    val modelPath: String = "nn-model.bin",
    private val hiddenSize: Int = 128
) {
    private val log = LoggerFactory.getLogger(NeuralNetworkClassifier::class.java)

    private var network: TextClassifierNeuralNetwork? = null

    fun isModelLoaded(): Boolean =
        network != null && network!!.labels.isNotEmpty() && network!!.words.isNotEmpty()

    /**
     * Trains the network from raw [trainData] in "LABEL;Description" format,
     * then automatically saves the model to [modelPath].
     *
     * @param trainData   Multi-line string where each line is "LABEL;Description".
     * @param epochs      Training iterations over the full dataset.
     * @param learningRate Gradient descent step size.
     */
    fun train(trainData: String, epochs: Int = 500, learningRate: Double = 0.1) {
        val lines = trainData.lines().filter { it.isNotBlank() }

        val labels = lines.map { it.split(";")[0].trim() }.distinct()
        // Split on whitespace runs and drop blank tokens (avoids empty-string feature pollution)
        val words  = lines.flatMap { it.split(";", limit = 2)[1].trim().lowercase()
            .split(Regex("\\s+")).filter { w -> w.isNotBlank() } }.distinct()

        // Inverse-frequency class weights to compensate for class imbalance
        val labelCounts = lines.groupingBy { it.split(";")[0].trim() }.eachCount()
        val avgCount = labelCounts.values.average()
        val classWeights = labels.associateWith { label -> avgCount / (labelCounts[label] ?: 1) }

        log.info("Training network — labels=${labels.size}, vocab=${words.size}, epochs=$epochs")

        // lambda=0.0: L1 regularization destroys input→hidden weights for sparse bag-of-words
        // inputs (word appears 1×/epoch → L1 drain >> gradient signal → weight → 0).
        // With 199 training samples, overfitting is not a concern.
        val nn = TextClassifierNeuralNetwork(words.size, hiddenSize, labels.size, lambda = 0.0, labels = labels, words = words)

        repeat(epochs) {
            lines.forEach { line ->
                val parts = line.split(";", limit = 2)
                val label = parts[0].trim()
                // Exact word-set membership instead of substring contains
                val descWords = parts[1].trim().lowercase().split(Regex("\\s+")).toSet()
                val inputVec = DoubleArray(words.size) { i -> if (words[i] in descWords) 1.0 else 0.0 }
                val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == label) 1.0 else 0.0 }
                val weight = classWeights[label] ?: 1.0
                nn.train(inputVec, targetVec, learningRate, weight)
            }
        }

        network = nn
        save()
    }

    /**
     * Persists the trained model to [modelPath] via [TextClassifierNeuralNetwork.saveModel].
     * @throws IllegalStateException if the network has not been trained yet.
     */
    fun save() {
        val nn = requireNotNull(network) { "No model to save — call train() first" }
        nn.saveModel(modelPath)
        log.info("Model saved to $modelPath (${File(modelPath).length()} bytes)")
    }

    /**
     * Loads the model from [modelPath] via [TextClassifierNeuralNetwork.loadModel].
     * Does nothing if the file does not exist.
     */
    fun load() {
        val file = File(modelPath)
        if (!file.exists()) {
            log.warn("Model file not found: $modelPath")
            return
        }

        network = TextClassifierNeuralNetwork.loadModel(modelPath)
        log.info("Model loaded from $modelPath — labels=${network!!.labels.size}, vocab=${network!!.words.size}")
    }

    /**
     * Predicts the category for a single [text] description.
     * Automatically calls [load] if the model is not yet in memory.
     *
     * @return Pair of predicted label and confidence (sigmoid output of winning neuron, 0–1).
     *         Returns `"DESCONHECIDA" to 0.0` when the model cannot be loaded.
     */
    fun predict(text: String): Pair<String, Double> {
        if (!isModelLoaded()) load()
        val nn = network ?: return UNKNOWN

        val words = nn.words
        val textWords = text.lowercase().split(Regex("\\s+")).toSet()
        val inputVec = DoubleArray(words.size) { i -> if (words[i] in textWords) 1.0 else 0.0 }
        val output = nn.predict(inputVec)
        val bestIdx = output.indices.maxByOrNull { output[it] } ?: 0
        return nn.labels[bestIdx] to output[bestIdx]
    }

    /**
     * Classifies [description] and wraps the result in Arrow [Either].
     *
     * @return [Either.Right] with [ClassificationResult] on success,
     *         [Either.Left] with [ClassificationError] on failure.
     */
    fun classify(description: String): Either<ClassificationError, ClassificationResult> {
        if (!isModelLoaded()) load()
        if (!isModelLoaded()) {
            return ClassificationError.ModelNotLoadedErr("Model not loaded — run train() first").left()
        }

        return runCatching { predict(description) }
            .fold(
                onSuccess = { (label, confidence) -> ClassificationResult(label, confidence).right() },
                onFailure = { e -> ClassificationError.ClassificationErr(e.message ?: "Unknown error").left() }
            )
    }

    /**
     * Classifies a batch of [descriptions].
     *
     * @return [Either.Right] with a list of [ClassificationResult] (in the same order),
     *         or [Either.Left] if a fatal error occurs during classification.
     */
    fun classifyAll(descriptions: List<String>): Either<ClassificationError, List<ClassificationResult>> {
        if (!isModelLoaded()) load()
        if (!isModelLoaded()) {
            return ClassificationError.ModelNotLoadedErr("Model not loaded — run train() first").left()
        }

        return runCatching {
            descriptions.map {
                classify(it).getOrNull() ?: ClassificationResult(UNKNOWN.first, UNKNOWN.second)
            }
        }.fold(
            onSuccess = { it.right() },
            onFailure = { e -> ClassificationError.ClassificationErr(e.message ?: "Unknown error").left() }
        )
    }

    data class ClassificationResult(val label: String, val confidence: Double)

    sealed class ClassificationError {
        data class ModelNotLoadedErr(val msg: String) : ClassificationError()
        data class ClassificationErr(val msg: String) : ClassificationError()
    }

    companion object {
        val UNKNOWN = "DESCONHECIDA" to 0.0
    }
}
