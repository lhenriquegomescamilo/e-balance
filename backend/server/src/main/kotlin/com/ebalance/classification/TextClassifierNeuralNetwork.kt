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

class TextClassifierNeuralNetwork(
    val inputSize: Int,
    val hiddenSize: Int,
    val outputSize: Int,
    val lambda: Double = 0.001,
    val labels: List<String> = emptyList(),
    val words: List<String> = emptyList()
) {
    private var weightsIH = Array(inputSize) { DoubleArray(hiddenSize) { Random().nextGaussian() * 0.1 } }
    private var weightsHO = Array(hiddenSize) { DoubleArray(outputSize) { Random().nextGaussian() * 0.1 } }
    private var biasH = DoubleArray(hiddenSize) { 0.0 }
    private var biasO = DoubleArray(outputSize) { 0.0 }

    private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
    private fun sigmoidDeriv(x: Double) = x * (1.0 - x)

    private fun softmax(logits: DoubleArray): DoubleArray {
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
        val sum = exps.sum()
        return DoubleArray(exps.size) { exps[it] / sum }
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

    companion object {
        const val MODEL_VERSION = 2

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
