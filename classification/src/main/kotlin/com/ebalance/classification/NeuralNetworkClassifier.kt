package com.ebalance.classification

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Adapter that wraps [TextClassifierNeuralNetwork] and provides a text-level interface:
 * training from raw "LABEL;Description" strings, model persistence, and text prediction.
 *
 * ## Model file format (version 1)
 * ```
 * int   version
 * int   labelsCount
 * UTF   label[0..labelsCount-1]
 * int   wordsCount
 * UTF   word[0..wordsCount-1]
 * ---   TextClassifierNeuralNetwork binary block (see its save/load) ---
 * ```
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
    private var labels: List<String> = emptyList()
    private var words: List<String> = emptyList()

    fun isModelLoaded(): Boolean = network != null && labels.isNotEmpty() && words.isNotEmpty()

    /**
     * Trains the network from raw [trainData] in "LABEL;Description" format,
     * then automatically saves the model to [modelPath].
     *
     * @param trainData  Multi-line string where each line is "LABEL;Description".
     * @param epochs     Training iterations over the full dataset.
     * @param learningRate Gradient descent step size.
     */
    fun train(trainData: String, epochs: Int = 500, learningRate: Double = 0.1) {
        val lines = trainData.lines().filter { it.isNotBlank() }

        labels = lines.map { it.split(";")[0].trim() }.distinct()
        words = lines.flatMap { it.split(";")[1].trim().lowercase().split(" ") }.distinct()

        log.info("Training network — labels=${labels.size}, vocab=${words.size}, epochs=$epochs")

        network = TextClassifierNeuralNetwork(words.size, hiddenSize, labels.size)

        repeat(epochs) {
            lines.forEach { line ->
                val parts = line.split(";", limit = 2)
                val inputVec = DoubleArray(words.size) { i ->
                    if (parts[1].trim().lowercase().contains(words[i])) 1.0 else 0.0
                }
                val targetVec = DoubleArray(labels.size) { i ->
                    if (labels[i] == parts[0].trim()) 1.0 else 0.0
                }
                network!!.train(inputVec, targetVec, learningRate)
            }
        }

        save()
    }

    /**
     * Persists labels, vocabulary, and network weights to [modelPath].
     * @throws IllegalStateException if the network has not been trained yet.
     */
    fun save() {
        val nn = requireNotNull(network) { "No model to save — call train() first" }

        DataOutputStream(BufferedOutputStream(FileOutputStream(modelPath))).use { out ->
            out.writeInt(FILE_VERSION)
            out.writeInt(labels.size)
            labels.forEach { out.writeUTF(it) }
            out.writeInt(words.size)
            words.forEach { out.writeUTF(it) }
            nn.save(out)
        }

        log.info("Model saved to $modelPath (${File(modelPath).length()} bytes)")
    }

    /**
     * Loads labels, vocabulary, and network weights from [modelPath].
     * Does nothing if the file does not exist.
     */
    fun load() {
        val file = File(modelPath)
        if (!file.exists()) {
            log.warn("Model file not found: $modelPath")
            return
        }

        DataInputStream(BufferedInputStream(FileInputStream(modelPath))).use { input ->
            val version = input.readInt()
            require(version == FILE_VERSION) { "Unsupported model version: $version (expected $FILE_VERSION)" }
            labels = List(input.readInt()) { input.readUTF() }
            words = List(input.readInt()) { input.readUTF() }
            network = TextClassifierNeuralNetwork.load(input)
        }

        log.info("Model loaded from $modelPath — labels=${labels.size}, vocab=${words.size}")
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

        val inputVec = DoubleArray(words.size) { i ->
            if (text.lowercase().contains(words[i])) 1.0 else 0.0
        }
        val output = nn.predict(inputVec)
        val bestIdx = output.indices.maxByOrNull { output[it] } ?: 0
        return labels[bestIdx] to output[bestIdx]
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

        return runCatching { descriptions.map { classify(it).getOrNull() ?: ClassificationResult(UNKNOWN.first, UNKNOWN.second) } }
            .fold(
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
        private const val FILE_VERSION = 1
        private val UNKNOWN = "DESCONHECIDA" to 0.0
    }
}
