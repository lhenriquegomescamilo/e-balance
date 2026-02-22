package com.ebalance.classification

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextClassifierTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var modelFile: File
    private lateinit var classifier: TextClassifier

    @BeforeEach
    fun setup() {
        modelFile = File(tempDir, "test-model.zip")
        classifier = TextClassifier(
            learningRate = 0.05,
            minLearningRate = 0.0001,
            epochs = 500,
            layerSize = 200,
            windowSize = 5,
            modelPath = modelFile.absolutePath
        )
    }

    @Test
    fun `should correctly classify Netflix as ASSINATURAS`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When
        val (label, score) = classifier.predictWithScore("Netflix")

        // Then
        assertEquals("ASSINATURAS", label, "Netflix should be classified as ASSINATURAS, got $label with score $score")
        assertTrue(score > 0.5, "Score should be > 0.5, got $score")
    }

    @Test
    fun `should correctly classify Apple as ASSINATURAS`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When
        val (label, score) = classifier.predictWithScore("Apple")

        // Then
        assertEquals("ASSINATURAS", label, "Apple should be classified as ASSINATURAS, got $label with score $score")
        assertTrue(score > 0.5, "Score should be > 0.5, got $score")
    }

    @Test
    fun `should correctly classify Spotify as ASSINATURAS`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When
        val (label, score) = classifier.predictWithScore("Spotify")

        // Then
        assertEquals("ASSINATURAS", label, "Spotify should be classified as ASSINATURAS, got $label with score $score")
        assertTrue(score > 0.5, "Score should be > 0.5, got $score")
    }

    @Test
    fun `should correctly classify Uber as TRANSPORTE`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When
        val (label, score) = classifier.predictWithScore("Uber")

        // Then
        assertEquals("TRANSPORTE", label, "Uber should be classified as TRANSPORTE, got $label with score $score")
        assertTrue(score > 0.5, "Score should be > 0.5, got $score")
    }

    @Test
    fun `should correctly classify McDonalds as RESTAURANTE`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When
        val (label, score) = classifier.predictWithScore("Mcdonalds")

        // Then
        assertEquals("RESTAURANTE", label, "Mcdonalds should be classified as RESTAURANTE, got $label with score $score")
        assertTrue(score > 0.5, "Score should be > 0.5, got $score")
    }

    @Test
    fun `should achieve high accuracy on training data`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // Test samples from each category
        val testSamples = listOf(
            "Netflix" to "ASSINATURAS",
            "Apple" to "ASSINATURAS",
            "Spotify" to "ASSINATURAS",
            "Uber" to "TRANSPORTE",
            "Mcdonalds" to "RESTAURANTE",
            "Posto Garzzo" to "COMBUSTIVEL",
            "Wise" to "TRANSFERENCIAS_BRASIL",
            "Nos Comunicacoes" to "TELEFONIA"
        )

        // When
        val result = classifier.validate(testSamples)

        // Then
        println(result)
        assertTrue(result.accuracy >= 0.75, "Accuracy should be >= 75%, got ${result.accuracy * 100}%")
    }

    @Test
    fun `should save model to file`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When - Save model
        assertTrue(modelFile.exists(), "Model file should exist after training")
        assertTrue(modelFile.length() > 0, "Model file should not be empty")
    }

    @Test
    fun `should handle unknown inputs gracefully`() {
        // Given
        val dataset = loadDataset()
        classifier.train(dataset)

        // When - Input that doesn't match anything
        val (label, score) = classifier.predictWithScore("XYZ123Unknown")

        // Then - Should return unknown or low confidence
        // Either returns "0" for unknown or a label with low confidence
        if (label != "0") {
            assertTrue(score < 0.7, "Unknown input should have low confidence, got $score")
        }
    }

    private fun loadDataset(): List<Pair<String, String>> {
        val inputStream = this::class.java.classLoader.getResourceAsStream("dataset/category.for.training.csv")
            ?: throw IllegalStateException("Dataset not found on classpath")
        
        return inputStream.bufferedReader().use { reader ->
            reader.lineSequence()
                .filter { it.isNotBlank() }
                .map { line ->
                    val parts = line.split(';', limit = 2)
                    parts[1].trim() to parts[0].trim()
                }
                .toList()
        }
    }
}
