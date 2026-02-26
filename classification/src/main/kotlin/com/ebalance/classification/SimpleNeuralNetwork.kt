package com.ebalance.classification


import kotlin.math.exp

class SimpleNeuralNetwork(
    private val inputSize: Int,
    private val outputSize: Int,
    private val learningRate: Double = 0.01
) {

    val weights = Array(outputSize) { DoubleArray(inputSize) { Math.random() - 0.5 } }

    val biases = DoubleArray(outputSize) { 0.0 }

    fun train(inputs: List<DoubleArray>, targets: List<Int>, epochs: Int = 10) {
        for(epoch in 1..epochs) {
            for ((x, y) in inputs.zip(targets)) {
                val outputs = predict(x)

                for (i in 0 until outputSize) {
                    val error = (if (i == y) 1.0 else 0.0) - outputs[i]
                    for (j in x.indices) {
                        weights[i][j] += learningRate * error * sigmoidDerivative(outputs[i]) * x[j]
                    }

                    biases[i] += learningRate * error * sigmoidDerivative(outputs[i])
                }
            }
        }
    }

    fun predict(input: DoubleArray): DoubleArray {
        return weights.mapIndexed { index, weight ->
            val z = weight.zip(input).sumOf { (w, i) -> w * i } + biases[index]
            sigmoid(z)
        }.toDoubleArray()
    }

    fun sigmoid(x: Double): Double = 1 / (1 + exp(-x))
    fun sigmoidDerivative(x: Double): Double = x * (1 - x)

}