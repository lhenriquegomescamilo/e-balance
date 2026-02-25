package com.ebalance.classification

import java.util.Random
import kotlin.math.exp

class TextClassifierNeuralNetwork(val inputSize: Int, val hiddenSize: Int, val outputSize: Int) {
    // Weights and Biases
    private var weightsIH = Array(inputSize) { DoubleArray(hiddenSize) { Random().nextGaussian() * 0.1 } }
    private var weightsHO = Array(hiddenSize) { DoubleArray(outputSize) { Random().nextGaussian() * 0.1 } }
    private var biasH = DoubleArray(hiddenSize) { 0.0 }
    private var biasO = DoubleArray(outputSize) { 0.0 }

    // Activation Function (Sigmoid)
    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
    private fun sigmoidDeriv(x: Double) = x * (1.0 - x)

    fun train(input: DoubleArray, target: DoubleArray, learningRate: Double) {
        // 1. FORWARD PASS
        val hidden = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = biasH[j]
            for (i in 0 until inputSize) sum += input[i] * weightsIH[i][j]
            hidden[j] = sigmoid(sum)
        }

        val output = DoubleArray(outputSize)
        for (j in 0 until outputSize) {
            var sum = biasO[j]
            for (i in 0 until hiddenSize) sum += hidden[i] * weightsHO[i][j]
            output[j] = sigmoid(sum)
        }

        // 2. BACKPROPAGATION (The Math)
        val outputErrors = DoubleArray(outputSize) { j -> target[j] - output[j] }
        val outputGradients = DoubleArray(outputSize) { j -> outputErrors[j] * sigmoidDeriv(output[j]) }

        val hiddenErrors = DoubleArray(hiddenSize)
        for (i in 0 until hiddenSize) {
            var error = 0.0
            for (j in 0 until outputSize) error += outputGradients[j] * weightsHO[i][j]
            hiddenErrors[i] = error
        }
        val hiddenGradients = DoubleArray(hiddenSize) { j -> hiddenErrors[j] * sigmoidDeriv(hidden[j]) }

        // 3. UPDATE WEIGHTS
        for (j in 0 until outputSize) {
            for (i in 0 until hiddenSize) weightsHO[i][j] += outputGradients[j] * hidden[i] * learningRate
            biasO[j] += outputGradients[j] * learningRate
        }
        for (j in 0 until hiddenSize) {
            for (i in 0 until inputSize) weightsIH[i][j] += hiddenGradients[j] * input[i] * learningRate
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

    val nn = TextClassifierNeuralNetwork(words.size, 10, labels.size)

    // Training Loop (Repeat 1000 times for accuracy)
    repeat(1000) {
        rawData.forEach { line ->
            val parts = line.split(";")
            val inputVec = DoubleArray(words.size) { i -> if (parts[1].lowercase().contains(words[i])) 1.0 else 0.0 }
            val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == parts[0]) 1.0 else 0.0 }
            nn.train(inputVec, targetVec, 0.1)
        }
    }

    // Test prediction
    val test = "Uber"
    val testVec = DoubleArray(words.size) { i -> if (test.lowercase().contains(words[i])) 1.0 else 0.0 }
    val result = nn.predict(testVec)

    val bestMatchIndex = result.indices.maxByOrNull { result[it] } ?: 0
    println("Input: $test -> Predicted: ${labels[bestMatchIndex]}")
}