package com.ebalance.classification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.io.File

class TextClassifierNeuralNetworkTest : DescribeSpec({

    val trainData = """
        HOSPITAL;Hospital Da Luz Sa
        COMBUSTIVEL;Posto Garzzo
        TRANSPORTE;Uber Trip
        DELIVERY;Uber Eats
        RESTAURANTE;Autogrill 7130
    """.trimIndent()

    // Parses vocabulary (words) and label list from raw "LABEL;Description" string data
    fun parseVocab(data: String): Pair<List<String>, List<String>> {
        val lines = data.lines().filter { it.isNotBlank() }
        val labels = lines.map { it.split(";")[0].trim() }.distinct()
        val words = lines.flatMap { it.split(";")[1].trim().lowercase().split(" ") }.distinct()
        return labels to words
    }

    // Runs training epochs on the network using raw "LABEL;Description" string data
    fun trainNetwork(
        nn: TextClassifierNeuralNetwork,
        labels: List<String>,
        words: List<String>,
        data: String,
        epochs: Int = 200,
        learningRate: Double = 0.1
    ) {
        val lines = data.lines().filter { it.isNotBlank() }
        repeat(epochs) {
            lines.forEach { line ->
                val parts = line.split(";", limit = 2)
                val inputVec = DoubleArray(words.size) { i ->
                    if (parts[1].trim().lowercase().contains(words[i])) 1.0 else 0.0
                }
                val targetVec = DoubleArray(labels.size) { i ->
                    if (labels[i] == parts[0].trim()) 1.0 else 0.0
                }
                nn.train(inputVec, targetVec, learningRate)
            }
        }
    }

    describe("TextClassifierNeuralNetwork") {

        describe("predict") {

            it("should return output of correct size") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)

                nn.predict(DoubleArray(words.size)) shouldHaveSize labels.size
            }

            it("should return values between 0 and 1 (sigmoid range)") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)

                nn.predict(DoubleArray(words.size) { 0.5 }).forEach {
                    it.shouldBeBetween(0.0, 1.0, 0.0)
                }
            }
        }

        describe("train") {

            it("should update the network output after a training step") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)

                val inputVec = DoubleArray(words.size) { 0.5 }
                val targetVec = DoubleArray(labels.size) { i -> if (i == 0) 1.0 else 0.0 }

                val before = nn.predict(inputVec).copyOf()
                nn.train(inputVec, targetVec, 0.1)
                val after = nn.predict(inputVec)

                // At least one output neuron should have changed
                before.zip(after.toList()).any { (b, a) -> b != a }.shouldBeTrue()
            }

            it("should move the top prediction towards the target label after many epochs") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)

                trainNetwork(nn, labels, words, trainData, epochs = 500)

                // For each sample, the argmax of predict should match the target label
                val lines = trainData.lines().filter { it.isNotBlank() }
                val correct = lines.count { line ->
                    val parts = line.split(";", limit = 2)
                    val label = parts[0].trim()
                    val inputVec = DoubleArray(words.size) { i ->
                        if (parts[1].trim().lowercase().contains(words[i])) 1.0 else 0.0
                    }
                    val predicted = nn.predict(inputVec)
                    val predictedLabel = labels[predicted.indices.maxByOrNull { predicted[it] } ?: 0]
                    predictedLabel == label
                }

                (correct > 0) shouldBe true
            }
        }

        describe("calculateR2") {

            it("should return 1.0 for perfect predictions") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                val actuals = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))

                nn.calculateR2(actuals, actuals) shouldBe 1.0
            }

            it("should return 0.0 when predicting the mean of actuals") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                val actuals = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
                val mean = doubleArrayOf(0.5, 0.5)

                nn.calculateR2(actuals, listOf(mean, mean)) shouldBe 0.0
            }

            it("should return 0.0 when all actual values are identical (ssTot = 0)") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                val actuals = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(1.0, 0.0))
                val predicteds = listOf(doubleArrayOf(0.5, 0.5), doubleArrayOf(0.5, 0.5))

                nn.calculateR2(actuals, predicteds) shouldBe 0.0
            }

            it("should return a negative score when predictions are worse than the mean") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                val actuals = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
                // Fully inverted predictions are much worse than predicting the mean
                val predicteds = listOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 0.0))

                nn.calculateR2(actuals, predicteds) shouldBeLessThan 0.0
            }
        }

        describe("save and load") {

            it("save should create a non-empty file at the given path") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(nn, labels, words, trainData)

                val file = File.createTempFile("nn-test", ".bin").also { it.deleteOnExit() }
                nn.save(file.absolutePath)

                file.exists() shouldBe true
                (file.length() > 0L) shouldBe true
            }

            it("load should restore a network that produces identical predictions") {
                val (labels, words) = parseVocab(trainData)
                val original = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(original, labels, words, trainData)

                val file = File.createTempFile("nn-test", ".bin").also { it.deleteOnExit() }
                original.save(file.absolutePath)

                val restored = TextClassifierNeuralNetwork.load(file.absolutePath)

                // Predictions from original and restored must be identical
                val input = DoubleArray(words.size) { i ->
                    if ("uber trip".contains(words[i])) 1.0 else 0.0
                }
                original.predict(input) shouldBe restored.predict(input)
            }

            it("load should restore network hyperparameters") {
                val (labels, words) = parseVocab(trainData)
                val original = TextClassifierNeuralNetwork(words.size, 64, labels.size, lambda = 0.005)

                val file = File.createTempFile("nn-test", ".bin").also { it.deleteOnExit() }
                original.save(file.absolutePath)

                val restored = TextClassifierNeuralNetwork.load(file.absolutePath)

                restored.inputSize shouldBe original.inputSize
                restored.hiddenSize shouldBe original.hiddenSize
                restored.outputSize shouldBe original.outputSize
                restored.lambda shouldBe original.lambda
            }
        }

        describe("score") {

            it("should return a value <= 1.0") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(nn, labels, words, trainData, epochs = 500)

                nn.score(trainData, trainData) shouldBeLessThanOrEqualTo 1.0
            }

            it("should return a higher R2 after training than before") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)

                val scoreBefore = nn.score(trainData, trainData)
                trainNetwork(nn, labels, words, trainData, epochs = 500)
                val scoreAfter = nn.score(trainData, trainData)

                scoreAfter shouldBeGreaterThan scoreBefore
            }

            it("should achieve a positive R2 on training data after sufficient training") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(nn, labels, words, trainData, epochs = 1000)

                nn.score(trainData, trainData) shouldBeGreaterThan 0.5
            }

            it("should silently skip blank lines in input data") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(nn, labels, words, trainData, epochs = 500)

                val dataWithBlanks = "\n\n$trainData\n\n"
                nn.score(dataWithBlanks, dataWithBlanks) shouldBeLessThanOrEqualTo 1.0
            }

            it("should use only testData labels for evaluation, ignoring unseen labels") {
                val (labels, words) = parseVocab(trainData)
                val nn = TextClassifierNeuralNetwork(words.size, 50, labels.size)
                trainNetwork(nn, labels, words, trainData, epochs = 300)

                // testData is a subset — score should still be computable
                val testData = """
                    HOSPITAL;Hospital Da Luz Sa
                    TRANSPORTE;Uber Trip
                """.trimIndent()

                nn.score(trainData, testData) shouldBeLessThanOrEqualTo 1.0
            }
        }
    }
})
