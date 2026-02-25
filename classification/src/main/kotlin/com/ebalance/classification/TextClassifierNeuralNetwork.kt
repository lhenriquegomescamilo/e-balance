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
    val lambda: Double = 0.001
) {
    // Weights and Biases
    private var weightsIH = Array(inputSize) { DoubleArray(hiddenSize) { Random().nextGaussian() * 0.1 } }
    private var weightsHO = Array(hiddenSize) { DoubleArray(outputSize) { Random().nextGaussian() * 0.1 } }
    private var biasH = DoubleArray(hiddenSize) { 0.0 }
    private var biasO = DoubleArray(outputSize) { 0.0 }

    // Activation Function (Sigmoid)
    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
    private fun sigmoidDeriv(x: Double) = x * (1.0 - x)

    /**
     * The Full Train Function
     * @param input: The "Bag of Words" vector (0s and 1s)
     * @param target: The "One-Hot" label vector (e.g., [1, 0, 0] for HOSPITAL)
     * @param learningRate: How fast the network learns (e.g., 0.1)
     */
    fun train(input: DoubleArray, target: DoubleArray, learningRate: Double) {

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

        // Calculate Output Layer
        val output = DoubleArray(outputSize)
        for (j in 0 until outputSize) {
            var sum = biasO[j]
            for (i in 0 until hiddenSize) {
                sum += hidden[i] * weightsHO[i][j]
            }
            output[j] = sigmoid(sum)
        }

        // --- 2. BACKPROPAGATION (Error Calculation) ---

        // Output Layer Errors & Gradients
        // Error = (Desired - Actual)
        val outputGradients = DoubleArray(outputSize) { j ->
            (target[j] - output[j]) * sigmoidDeriv(output[j])
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
        return DoubleArray(outputSize) { j ->
            sigmoid(biasO[j] + hidden.indices.sumOf { i -> hidden[i] * weightsHO[i][j] })
        }
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

        val labels = trainLines.map { it.split(";")[0].trim() }.distinct()
        val words = trainLines.flatMap { it.split(";")[1].trim().lowercase().split(" ") }.distinct()

        val actuals = mutableListOf<DoubleArray>()
        val predicteds = mutableListOf<DoubleArray>()

        for (line in testLines) {
            val parts = line.split(";", limit = 2)
            if (parts.size < 2) continue
            val label = parts[0].trim()
            val description = parts[1].trim()

            val inputVec = DoubleArray(words.size) { i -> if (description.lowercase().contains(words[i])) 1.0 else 0.0 }
            val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == label) 1.0 else 0.0 }

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
     * Saves the network to a standalone binary file at [path].
     */
    fun save(path: String) {
        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { save(it) }
    }

    companion object {
        /**
         * Restores a [TextClassifierNeuralNetwork] from a [DataInputStream].
         * The stream must be positioned at the start of data written by [save].
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
         */
        fun load(path: String): TextClassifierNeuralNetwork {
            return DataInputStream(BufferedInputStream(FileInputStream(path))).use { load(it) }
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
