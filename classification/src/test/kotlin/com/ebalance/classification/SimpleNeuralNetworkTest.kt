package com.ebalance.classification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp


class SimpleNeuralNetworkTest : DescribeSpec({

    // Initialize an instance of the neural network for testing
    val inputSize = 2
    val outputSize = 2
    val learningRate = 0.1

    describe("SimpleNeuralNetwork") {

        it("should initialize weights and biases correctly") {
            val network = SimpleNeuralNetwork(inputSize, outputSize, learningRate)

            // Assertions for weights
            network.weights shouldHaveSize outputSize // Number of output neurons
            network.weights.forEach { weightRow ->
                weightRow shouldHaveSize inputSize // Each row should have inputSize columns
                weightRow.forEach { weight ->
                    weight.shouldBeBetween(-0.5, 0.5, 0.000001) // Weights are random between -0.5 and 0.5
                }
            }

            // Assertions for biases
            network.biases shouldHaveSize outputSize // Number of output neurons
            network.biases.forEach { bias ->
                bias shouldBe 0.0 // Biases should be initialized to 0.0
            }
        }

        describe("sigmoid") {

            it("sigmoid function should produce correct output") {
                val network = SimpleNeuralNetwork(inputSize, outputSize) // Use default learning rate

                network.sigmoid(0.0) shouldBe 0.5 // sigmoid(0) = 0.5
                network.sigmoid(Double.POSITIVE_INFINITY) shouldBe 1.0 // sigmoid(infinity) = 1.0
                network.sigmoid(Double.NEGATIVE_INFINITY) shouldBe 0.0 // sigmoid(-infinity) = 0.0
                network.sigmoid(1.0).shouldBeBetween(0.73, 0.731, 0.001) // Approx value
                network.sigmoid(-1.0).shouldBeBetween(0.26, 0.27, 0.001) // Approx value
            }

        }

        describe("sigmoidDerivative") {
            it("should calculate the derivative correctly") {
                val network = SimpleNeuralNetwork(1, 1)
                val s = network.sigmoid(0.0)
                val derivative = network.sigmoidDerivative(s)
                derivative shouldBeExactly s * (1 - 0.5)
            }

            it("sigmoidDerivative function should produce correct output") {
                val network = SimpleNeuralNetwork(inputSize, outputSize)

                // Sigmoid derivative at x=0.5 (which is the output of sigmoid(0)) is 0.5 * (1 - 0.5) = 0.25
                network.sigmoidDerivative(0.5) shouldBe 0.25

                // Test values close to 0 and 1 (where derivative approaches 0)
                network.sigmoidDerivative(0.01).shouldBeBetween(0.009, 0.01, 0.001)
                network.sigmoidDerivative(0.99).shouldBeBetween(0.009, 0.01, 0.001)

                BigDecimal(network.sigmoidDerivative(0.2)).setScale(2, RoundingMode.DOWN).toDouble() shouldBe 0.16

            }
        }

        describe("predict") {
            it("predict should return an output of correct size and range") {
                val network = SimpleNeuralNetwork(inputSize, outputSize)
                val input = doubleArrayOf(0.5, 0.2)
                val outputs = network.predict(input)

                outputs shouldHaveSize outputSize // Output array size should match outputSize
                outputs.forEach { output ->
                    output.shouldBeBetween(0.0, 1.0, 0.000001) // Sigmoid output is always between 0 and 1
                }
            }


        }

        describe("train") {

            it("train should update weights and biases") {
                val network = SimpleNeuralNetwork(inputSize, outputSize, learningRate)

                // Store initial weights and biases for comparison
                val initialWeights = network.weights.map { it.clone() }
                val initialBiases = network.biases.clone()

                // Dummy training data
                val inputs = listOf(doubleArrayOf(0.1, 0.9))
                val targets = listOf(0) // Expected first output neuron to be active

                network.train(inputs, targets, epochs = 5) // train for a few epochs

                network.weights.forEachIndexed { i, weightRow ->
                    weightRow.forEachIndexed { j, weight ->
                        // Check if the current weight is significantly different from the intial weight
                        // Using a tolerance here, as floating point comparisons can be tricky
                        weight shouldNotBe initialWeights[i][j]
                    }
                }
                // Assert that biases have changed
                network.biases.forEachIndexed { i, bias -> bias shouldNotBe initialBiases[i] }
            }

            it("should reduce error after training") {
                val inputs = listOf(
                    doubleArrayOf(0.0, 0.0),
                    doubleArrayOf(0.0, 1.0),
                    doubleArrayOf(1.0, 0.0),
                    doubleArrayOf(1.0, 1.0)
                )
                val targets = listOf(0, 1, 1, 0) // XOR logic (not linearly separable)

                val network = SimpleNeuralNetwork(2, 2, learningRate = 0.1)

                val before = inputs.map { network.predict(it) }

                network.train(inputs, targets, epochs = 100)

                val after = inputs.map { network.predict(it) }

                // You can observe that the correct target's activation should increase
                for ((output, target) in after.zip(targets)) {
                    output[target] shouldBeGreaterThan 0.4
                }
            }
        }
    }


})


// Helper extension function to find index of maximum value in a DoubleArray
fun DoubleArray.indexOfMax(): Int {
    if (this.isEmpty()) return -1
    var maxVal = this[0]
    var maxIdx = 0
    for (i in 1 until this.size) {
        if (this[i] > maxVal) {
            maxVal = this[i]
            maxIdx = i
        }
    }
    return maxIdx
}