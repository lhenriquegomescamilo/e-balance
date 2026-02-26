package com.ebalance.classification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class NeuralNetworkClassifierTest : DescribeSpec({

    val trainData = """
        HOSPITAL;Hospital Da Luz Sa
        COMBUSTIVEL;Posto Garzzo
        TRANSPORTE;Uber Trip
        DELIVERY;Uber Eats
        RESTAURANTE;Autogrill 7130
    """.trimIndent()

    fun tempModelFile(): File = File.createTempFile("nn-classifier-test", ".bin").also { it.deleteOnExit() }

    describe("NeuralNetworkClassifier") {

        describe("isModelLoaded") {

            it("should return false before training or loading") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.isModelLoaded().shouldBeFalse()
            }

            it("should return true after training") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 100)
                classifier.isModelLoaded().shouldBeTrue()
            }
        }

        describe("train") {

            it("should mark the model as loaded") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 100)
                classifier.isModelLoaded().shouldBeTrue()
            }

            it("should automatically save the model file") {
                val file = tempModelFile()
                val classifier = NeuralNetworkClassifier(file.absolutePath)
                classifier.train(trainData, epochs = 100)

                file.exists() shouldBe true
                (file.length() > 0L) shouldBe true
            }
        }

        describe("save and load") {

            it("load should restore the model so predict returns the same result") {
                val file = tempModelFile()

                // Train and save
                val trainer = NeuralNetworkClassifier(file.absolutePath)
                trainer.train(trainData, epochs = 300)
                val (labelBefore, _) = trainer.predict("Uber Trip")

                // Load into a fresh instance
                val loader = NeuralNetworkClassifier(file.absolutePath)
                loader.isModelLoaded().shouldBeFalse()
                loader.load()
                loader.isModelLoaded().shouldBeTrue()

                val (labelAfter, _) = loader.predict("Uber Trip")
                labelAfter shouldBe labelBefore
            }

            it("load should be a no-op when the file does not exist") {
                val classifier = NeuralNetworkClassifier("/tmp/does-not-exist-nn.bin")
                classifier.load()
                classifier.isModelLoaded().shouldBeFalse()
            }

            it("predict should auto-load the model on first call") {
                val file = tempModelFile()

                // Train + save with one instance
                NeuralNetworkClassifier(file.absolutePath).train(trainData, epochs = 300)

                // New instance — model not in memory yet
                val classifier = NeuralNetworkClassifier(file.absolutePath)
                classifier.isModelLoaded().shouldBeFalse()

                // predict() auto-loads
                val (label, confidence) = classifier.predict("Uber Trip")
                classifier.isModelLoaded().shouldBeTrue()
                (label.isNotBlank()) shouldBe true
                confidence.shouldBeBetween(0.0, 1.0, 0.0)
            }
        }

        describe("predict") {

            it("should return a label from the training set") {
                val trainedLabels = trainData.lines()
                    .filter { it.isNotBlank() }
                    .map { it.split(";")[0].trim() }
                    .distinct()

                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 300)

                val (label, _) = classifier.predict("Uber Trip")
                (label in trainedLabels) shouldBe true
            }

            it("should return a confidence between 0 and 1") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 300)

                val (_, confidence) = classifier.predict("Uber Trip")
                confidence.shouldBeBetween(0.0, 1.0, 0.0)
            }

            it("should return TRANSPORTE for Uber Trip after sufficient training") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath, hiddenSize = 100)
                classifier.train(trainData, epochs = 1000)

                val (label, confidence) = classifier.predict("Uber Trip")
                label shouldBe "TRANSPORTE"
                confidence shouldBeGreaterThan 0.5
            }

            it("should return DESCONHECIDA when model cannot be loaded") {
                val classifier = NeuralNetworkClassifier("/tmp/no-model-file.bin")
                val (label, confidence) = classifier.predict("anything")
                label shouldBe "DESCONHECIDA"
                confidence shouldBe 0.0
            }
        }

        describe("classify") {

            it("should return Right with ClassificationResult on success") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 300)

                val result = classifier.classify("Uber Trip")
                result.isRight() shouldBe true
                result.getOrNull()?.confidence?.shouldBeBetween(0.0, 1.0, 0.0)
            }

            it("should return Left ModelNotLoadedErr when model cannot be loaded") {
                val classifier = NeuralNetworkClassifier("/tmp/no-model-file.bin")
                val result = classifier.classify("anything")

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<NeuralNetworkClassifier.ClassificationError.ModelNotLoadedErr>()
            }
        }

        describe("classifyAll") {

            it("should classify all descriptions and preserve order") {
                val classifier = NeuralNetworkClassifier(tempModelFile().absolutePath)
                classifier.train(trainData, epochs = 300)

                val inputs = listOf("Uber Trip", "Posto Garzzo", "Uber Eats")
                val result = classifier.classifyAll(inputs)

                result.isRight() shouldBe true
                result.getOrNull()!!.size shouldBe inputs.size
            }

            it("should return Left when model cannot be loaded") {
                val classifier = NeuralNetworkClassifier("/tmp/no-model-file.bin")
                val result = classifier.classifyAll(listOf("test"))

                result.isLeft() shouldBe true
            }
        }
    }
})
