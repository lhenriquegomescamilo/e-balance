package com.ebalance.classification

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class TextClassifierNeuralNetwork(
    val inputSize: Int,
    val hiddenSize: Int,
    val outputSize: Int,
    val lambda: Double = 0.001,
    val labels: List<String> = emptyList(),
    val words: List<String> = emptyList()
) {
    // Weights and Biases
    private var weightsIH = Array(inputSize) { DoubleArray(hiddenSize) { Random().nextGaussian() * 0.1 } }
    private var weightsHO = Array(hiddenSize) { DoubleArray(outputSize) { Random().nextGaussian() * 0.1 } }
    private var biasH = DoubleArray(hiddenSize) { 0.0 }
    private var biasO = DoubleArray(outputSize) { 0.0 }

    // Activation Function (Sigmoid)
    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
    private fun sigmoidDeriv(x: Double) = x * (1.0 - x)

    // Softmax for multi-class output (numerically stable)
    private fun softmax(logits: DoubleArray): DoubleArray {
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
        val sum = exps.sum()
        return DoubleArray(exps.size) { exps[it] / sum }
    }

    /**
     * The Full Train Function
     * @param input: The "Bag of Words" vector (0s and 1s)
     * @param target: The "One-Hot" label vector (e.g., [1, 0, 0] for HOSPITAL)
     * @param learningRate: How fast the network learns (e.g., 0.1)
     * @param weight: Per-sample importance weight, used for class balancing (default 1.0)
     */
    fun train(input: DoubleArray, target: DoubleArray, learningRate: Double, weight: Double = 1.0) {

        // --- 1. FORWARD PASS ---
        // Calculate Hidden Layer
        val hidden = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = biasH[j]
            for (i in 0 until inputSize) {
                sum += input[i] * weightsIH[i][j]
            }
            hidden[j] = sigmoid(sum)
        }

        // Calculate Output Layer (raw logits → softmax probabilities)
        val rawOutput = DoubleArray(outputSize)
        for (j in 0 until outputSize) {
            var sum = biasO[j]
            for (i in 0 until hiddenSize) {
                sum += hidden[i] * weightsHO[i][j]
            }
            rawOutput[j] = sum
        }
        val output = softmax(rawOutput)

        // --- 2. BACKPROPAGATION (Error Calculation) ---

        // Output Layer Gradients: cross-entropy + softmax simplifies to (target - output)
        // Scaled by per-sample weight for class balancing
        val outputGradients = DoubleArray(outputSize) { j ->
            (target[j] - output[j]) * weight
        }

        // Hidden Layer Errors & Gradients
        val hiddenGradients = DoubleArray(hiddenSize) { i ->
            var error = 0.0
            for (j in 0 until outputSize) {
                error += outputGradients[j] * weightsHO[i][j]
            }
            error * sigmoidDeriv(hidden[i])
        }

        // --- 3. WEIGHT UPDATES (With Lasso / L1) ---

        // Update Hidden -> Output Weights
        for (j in 0 until outputSize) {
            for (i in 0 until hiddenSize) {
                // Calculate Lasso Penalty: lambda * sign of the weight
                val l1Penalty = lambda * sign(weightsHO[i][j])

                // New Weight = Old Weight + Gradient Adjustment - Penalty Adjustment
                // We multiply both by learningRate
                weightsHO[i][j] += (outputGradients[j] * hidden[i] * learningRate) - (learningRate * l1Penalty)
            }
            // Update Bias (Usually we don't apply Lasso to Biases)
            biasO[j] += outputGradients[j] * learningRate
        }

        // Update Input -> Hidden Weights
        for (j in 0 until hiddenSize) {
            for (i in 0 until inputSize) {
                // Calculate Lasso Penalty
                val l1Penalty = lambda * sign(weightsIH[i][j])

                weightsIH[i][j] += (hiddenGradients[j] * input[i] * learningRate) - (learningRate * l1Penalty)
            }
            biasH[j] += hiddenGradients[j] * learningRate
        }
    }

    fun predict(input: DoubleArray): DoubleArray {
        val hidden = DoubleArray(hiddenSize) { j ->
            sigmoid(biasH[j] + input.indices.sumOf { i -> input[i] * weightsIH[i][j] })
        }
        val logits = DoubleArray(outputSize) { j ->
            biasO[j] + hidden.indices.sumOf { i -> hidden[i] * weightsHO[i][j] }
        }
        return softmax(logits)
    }

    /**
     * Computes the R² score using raw string data instead of pre-encoded vectors.
     *
     * @param trainData Multi-line string in "LABEL;Description" format, used to derive
     *                  the vocabulary and label index. Must match the data used to
     *                  initialize the network's [inputSize] and [outputSize].
     * @param testData  Multi-line string in "LABEL;Description" format to evaluate.
     * @return R² score in (-∞, 1.0], where 1.0 is a perfect fit.
     */
    fun score(trainData: String, testData: String): Double {
        val trainLines = trainData.lines().filter { it.isNotBlank() }
        val testLines = testData.lines().filter { it.isNotBlank() }

        // Prefer the network's own labels/words when available (e.g. loaded from disk)
        val effectiveLabels = if (this.labels.isNotEmpty()) this.labels
            else trainLines.map { it.split(";")[0].trim() }.distinct()
        val effectiveWords = if (this.words.isNotEmpty()) this.words
            else trainLines.flatMap { it.split(";", limit = 2)[1].trim().lowercase()
                .split(Regex("\\s+")).filter { it.isNotBlank() } }.distinct()

        val actuals = mutableListOf<DoubleArray>()
        val predicteds = mutableListOf<DoubleArray>()

        for (line in testLines) {
            val parts = line.split(";", limit = 2)
            if (parts.size < 2) continue
            val label = parts[0].trim()
            val description = parts[1].trim()

            // Use exact word-set membership (not substring contains)
            val descWords = description.lowercase().split(Regex("\\s+")).toSet()
            val inputVec = DoubleArray(effectiveWords.size) { i -> if (effectiveWords[i] in descWords) 1.0 else 0.0 }
            val targetVec = DoubleArray(effectiveLabels.size) { i -> if (effectiveLabels[i] == label) 1.0 else 0.0 }

            actuals.add(targetVec)
            predicteds.add(predict(inputVec))
        }

        return calculateR2(actuals, predicteds)
    }

    fun calculateR2(actuals: List<DoubleArray>, predicteds: List<DoubleArray>): Double {
        val numSamples = actuals.size
        val numOutputs = actuals[0].size

        // 1. Calculate the mean of the actual values
        val means = DoubleArray(numOutputs)
        for (i in 0 until numSamples) {
            for (j in 0 until numOutputs) {
                means[j] += actuals[i][j]
            }
        }
        for (j in 0 until numOutputs) means[j] /= numSamples.toDouble()

        var ssRes = 0.0
        var ssTot = 0.0

        // 2. Sum the squares
        for (i in 0 until numSamples) {
            for (j in 0 until numOutputs) {
                ssRes += (actuals[i][j] - predicteds[i][j]).pow(2.0)
                ssTot += (actuals[i][j] - means[j]).pow(2.0)
            }
        }

        // 3. Final calculation
        return if (ssTot == 0.0) 0.0 else 1.0 - (ssRes / ssTot)
    }

    /**
     * Serializes the network weights and biases to [out].
     * Intended for embedding inside a larger model file (e.g. [NeuralNetworkClassifier]).
     */
    fun save(out: DataOutputStream) {
        out.writeInt(inputSize)
        out.writeInt(hiddenSize)
        out.writeInt(outputSize)
        out.writeDouble(lambda)
        for (i in 0 until inputSize) for (j in 0 until hiddenSize) out.writeDouble(weightsIH[i][j])
        for (i in 0 until hiddenSize) for (j in 0 until outputSize) out.writeDouble(weightsHO[i][j])
        for (j in 0 until hiddenSize) out.writeDouble(biasH[j])
        for (j in 0 until outputSize) out.writeDouble(biasO[j])
    }

    /**
     * Saves the network weights to a standalone binary file at [path].
     * This file contains only the weight block — use [saveModel] for a fully self-contained file.
     */
    fun save(path: String) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { save(it) }
    }

    /**
     * Saves the complete self-contained model — labels, vocabulary, and network weights — to [path].
     * The resulting file can be fully restored (including labels and words) via [loadModel].
     */
    fun saveModel(path: String) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { out ->
            out.writeInt(MODEL_VERSION)
            out.writeInt(labels.size)
            labels.forEach { out.writeUTF(it) }
            out.writeInt(words.size)
            words.forEach { out.writeUTF(it) }
            save(out)
        }
    }

    companion object {
        // Version history:
        //  1 – sigmoid output + MSE loss (broken: L1 over-regularization destroyed input weights)
        //  2 – softmax output + cross-entropy loss, lambda=0 for production training
        const val MODEL_VERSION = 2

        /**
         * Restores a [TextClassifierNeuralNetwork] from a [DataInputStream].
         * The stream must be positioned at the start of data written by [save].
         * Labels and vocabulary are NOT included — use [loadModel] to restore a full model.
         */
        fun load(input: DataInputStream): TextClassifierNeuralNetwork {
            val inputSize = input.readInt()
            val hiddenSize = input.readInt()
            val outputSize = input.readInt()
            val lambda = input.readDouble()
            val nn = TextClassifierNeuralNetwork(inputSize, hiddenSize, outputSize, lambda)
            for (i in 0 until inputSize) for (j in 0 until hiddenSize) nn.weightsIH[i][j] = input.readDouble()
            for (i in 0 until hiddenSize) for (j in 0 until outputSize) nn.weightsHO[i][j] = input.readDouble()
            for (j in 0 until hiddenSize) nn.biasH[j] = input.readDouble()
            for (j in 0 until outputSize) nn.biasO[j] = input.readDouble()
            return nn
        }

        /**
         * Loads a [TextClassifierNeuralNetwork] from a standalone binary file at [path].
         * This only reads the raw weight block — use [loadModel] to restore a full self-contained model.
         */
        fun load(path: String): TextClassifierNeuralNetwork =
            DataInputStream(BufferedInputStream(FileInputStream(path))).use { load(it) }

        /**
         * Restores a fully self-contained [TextClassifierNeuralNetwork] — including labels and
         * vocabulary — from a file previously written by [saveModel].
         */
        fun loadModel(path: String): TextClassifierNeuralNetwork {
            DataInputStream(BufferedInputStream(FileInputStream(path))).use { input ->
                val version = input.readInt()
                require(version == MODEL_VERSION) {
                    "Unsupported model version: $version (expected $MODEL_VERSION)"
                }
                val labels    = List(input.readInt()) { input.readUTF() }
                val words     = List(input.readInt()) { input.readUTF() }
                val inputSize  = input.readInt()
                val hiddenSize = input.readInt()
                val outputSize = input.readInt()
                val lambda     = input.readDouble()
                val nn = TextClassifierNeuralNetwork(inputSize, hiddenSize, outputSize, lambda, labels, words)
                for (i in 0 until inputSize)  for (j in 0 until hiddenSize)  nn.weightsIH[i][j] = input.readDouble()
                for (i in 0 until hiddenSize) for (j in 0 until outputSize)  nn.weightsHO[i][j] = input.readDouble()
                for (j in 0 until hiddenSize) nn.biasH[j] = input.readDouble()
                for (j in 0 until outputSize) nn.biasO[j] = input.readDouble()
                return nn
            }
        }
    }
}

fun main() {
    val rawData = """
        HOSPITAL;Hospital Da Luz Sa
        COMBUSTIVEL;Posto Garzzo
        TRANSPORTE;Uber Trip
        DELIVERY;Uber Eats
        RESTAURANTE;Autogrill 7130
    """.trimIndent().lines()

    val labels = rawData.map { it.split(";")[0] }.distinct()
    val words = rawData.flatMap { it.split(";")[1].lowercase().split(" ") }.distinct()

    val nn = TextClassifierNeuralNetwork(words.size, 1000, labels.size, lambda = 100.0)

    // Training Loop (Repeat 1000 times for accuracy)
    val timeForTraining = measureTime {
        repeat(1000) {
            rawData.forEach { line ->
                val parts = line.split(";")
                val inputVec =
                    DoubleArray(words.size) { i -> if (parts[1].lowercase().contains(words[i])) 1.0 else 0.0 }
                val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == parts[0]) 1.0 else 0.0 }
                nn.train(inputVec, targetVec, 0.1)
            }
        }
    }

    // Test prediction
    val test = "Uber Trip"
    val testVec = DoubleArray(words.size) { i -> if (test.lowercase().contains(words[i])) 1.0 else 0.0 }
    val result = measureTimedValue { nn.predict(testVec) }

    val bestMatchIndex = result.value.indices.maxByOrNull { result.value[it] } ?: 0
    println("Training time: $timeForTraining, time for predict: ${result.duration} Input: $test -> Predicted: ${labels[bestMatchIndex]}")
}
