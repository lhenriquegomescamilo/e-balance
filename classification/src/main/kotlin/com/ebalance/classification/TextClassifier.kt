package com.ebalance.classification

import java.io.File
import java.text.Normalizer
import java.util.regex.Pattern
import org.apache.commons.text.similarity.LevenshteinDistance
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory
import org.slf4j.LoggerFactory

class TextClassifier(
    private val learningRate: Double = 0.05,
    private val minLearningRate: Double = 0.0001,
    private val epochs: Int = 500,
    private val layerSize: Int = 200,
    private val windowSize: Int = 5,
    private val modelPath: String = "model.zip"
) {

    private val log = LoggerFactory.getLogger(TextClassifier::class.java)

    private val businessStopWords = setOf(
        "lda", "l d a", "Lda", "Unipessoal", "unipessoal", "sa", "s.a.", "s a", "limitada", "sociedade",
        "portugal", "portuguesa", "e", "de", "da", "do", "das", "dos", "com",
        "comunicacoes", "actividades", "gestao", "administracao", "servicos",
        "56413 Cc Mar"
    )

    private lateinit var paragraphVectors: ParagraphVectors
    private val tokenizerFactory = DefaultTokenizerFactory().apply {
        tokenPreProcessor = CommonPreprocessor()
    }

    // Safety net: Store cleaned training data for Fuzzy Matching fallback
    private var cleanedTrainingData: List<Pair<String, String>> = listOf()
    private val levenshtein = LevenshteinDistance()

    /**
     * Helper to remove noise words so the model focuses on the Brand Name
     */
    private fun cleanText(text: String): String {
        return text.lowercase()
            .removeAccents()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation/symbols
            .split(" ")
            .filter { it !in businessStopWords && it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }


    // Define the extension function
    private fun String.removeAccents(): String {
        val normalizedString = Normalizer.normalize(this, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(normalizedString).replaceAll("")
    }

    inner class SimpleIterator(val data: List<Pair<String, String>>) : LabelAwareSentenceIterator {
        private var index = 0
        private var preProcessor: SentencePreProcessor? = null
        override fun hasNext(): Boolean = index < data.size
        override fun nextSentence(): String {
            val text = data[index++].first
            return preProcessor?.preProcess(text) ?: text
        }

        override fun currentLabel(): String = data[index - 1].second
        override fun currentLabels(): List<String> = listOf(data[index - 1].second)
        override fun reset() {
            index = 0
        }

        override fun finish() {}
        override fun getPreProcessor(): SentencePreProcessor? = preProcessor
        override fun setPreProcessor(p0: SentencePreProcessor?) {
            this.preProcessor = p0
        }
    }

    fun train(dataset: List<Pair<String, String>>) {
        log.info("Starting model training with ${dataset.size} samples")
        log.debug("Training parameters: learningRate=$learningRate, minLearningRate=$minLearningRate, epochs=$epochs, layerSize=$layerSize, windowSize=$windowSize")
        
        // 1. Pre-clean the dataset
        this.cleanedTrainingData = dataset.map { cleanText(it.first) to it.second }
        log.debug("Cleaned ${cleanedTrainingData.size} training samples")

        val iterator = SimpleIterator(cleanedTrainingData)

        paragraphVectors = ParagraphVectors.Builder()
            .learningRate(learningRate)
            .minLearningRate(minLearningRate)
            .batchSize(cleanedTrainingData.size)
            .epochs(epochs)
            .layerSize(layerSize)
            .windowSize(windowSize)
            .iterate(iterator)
            .trainWordVectors(true)
            .tokenizerFactory(tokenizerFactory)
            .stopWords(businessStopWords.toList())
            .seed(42L)
            .build()

        log.debug("Fitting ParagraphVectors model...")
        paragraphVectors.fit()
        log.info("Model training completed. Vocabulary size: ${paragraphVectors.vocab().numWords()}")

        // Persist the trained model
        runCatching {
            WordVectorSerializer.writeParagraphVectors(paragraphVectors, File(modelPath))
            log.info("Model saved to: $modelPath")
        }.onFailure { 
            log.error("Failed to save model to $modelPath", it)
            it.printStackTrace() 
        }
    }

    fun load() {
        val f = File(modelPath)
        if (f.exists()) {
            log.info("Loading model from: $modelPath")
            runCatching {
                WordVectorSerializer.readParagraphVectors(f).also {
                    it.tokenizerFactory = tokenizerFactory
                    paragraphVectors = it
                    log.info("Model loaded successfully. Vocabulary size: ${paragraphVectors.vocab().numWords()}")
                }
            }.onFailure { 
                log.error("Failed to load model from $modelPath", it)
                it.printStackTrace() 
            }
        } else {
            log.warn("Model file not found: $modelPath")
        }
    }

    fun isModelLoaded(): Boolean = ::paragraphVectors.isInitialized

    fun predictWithScore(
        text: String,
        unknown: Pair<String, Double> = "DESCONHECIDA" to 0.0,
        aiThreshold: Double = 0.50
    ): Pair<String, Double> {

        log.debug("Classifying input: '$text'")

        // Ensure model is loaded
        if (!::paragraphVectors.isInitialized) {
            log.debug("Model not initialized, loading...")
            load()
        }
        if (!::paragraphVectors.isInitialized) {
            log.warn("Model failed to load, returning unknown")
            return unknown
        }

        val cleanedInput = cleanText(text)
        log.debug("Cleaned input: '$cleanedInput'")

        // 1. Check if model knows the words
        val tokens = tokenizerFactory.create(cleanedInput).tokens
        log.debug("Tokenized: {}", tokens)
        val knownWords = tokens.filter { paragraphVectors.vocab().containsWord(it) }
        log.debug("Known words: {}", knownWords)

        var aiLabel = "0"
        var aiScore = 0.0

        if (knownWords.isNotEmpty()) {
            aiLabel = paragraphVectors.nearestLabels(cleanedInput, 1).firstOrNull() ?: "0"
            aiScore = runCatching {
                paragraphVectors.similarityToLabel(cleanedInput, aiLabel)
            }.getOrDefault(0.0)
            log.debug("ML prediction: label=$aiLabel, score=$aiScore")
        } else {
            log.debug("No known words found, skipping ML classification")
        }

        // 2. Fallback to Fuzzy Matching if AI is unconfident or words are unknown
        if (aiScore < aiThreshold) {
            log.debug("AI score $aiScore below threshold $aiThreshold, trying fuzzy matching...")
            val fuzzyMatch = findBestFuzzyMatch(cleanedInput)
            // Lower threshold for fuzzy matching to 50%
            if (fuzzyMatch != null && fuzzyMatch.second > 0.50) {
                log.debug("Fuzzy match found: label=${fuzzyMatch.first}, similarity=${fuzzyMatch.second}")
                return fuzzyMatch
            } else {
                log.debug("No fuzzy match found with sufficient similarity")
            }
        }

        return takeIf { aiScore >= aiThreshold }?.let { aiLabel to aiScore } ?: unknown
    }

    private fun findBestFuzzyMatch(text: String): Pair<String, Double>? {
        if (cleanedTrainingData.isEmpty() || text.isEmpty()) return null

        // Try matching full text first
        val fullMatch = cleanedTrainingData.map { (trainedName, label) ->
            val distance = levenshtein.apply(text, trainedName)
            val maxLength = maxOf(text.length, trainedName.length)
            val similarity = if (maxLength == 0) 0.0 else (1.0 - (distance.toDouble() / maxLength))
            label to similarity
        }.maxByOrNull { it.second }

        // If full text match is poor, try matching individual tokens
        val tokens = text.split(" ").filter { it.isNotBlank() }
        if (tokens.size > 1) {
            val tokenMatches = tokens.mapNotNull { token ->
                cleanedTrainingData
                    .filter { (trainedName, _) -> trainedName.contains(token) || token.contains(trainedName) }
                    .maxByOrNull { (_, label) -> label }
                    ?.let { (_, label) -> label to 0.8 } // Moderate confidence for partial match
            }
            
            if (tokenMatches.isNotEmpty()) {
                // Return the most common label among token matches
                val bestLabel = tokenMatches.groupBy { it.first }.maxByOrNull { it.value.size }?.key
                val avgConfidence = tokenMatches.map { it.second }.average()
                if (bestLabel != null && (fullMatch?.second ?: 0.0) < avgConfidence) {
                    return bestLabel to avgConfidence
                }
            }
        }

        return fullMatch
    }

    /**
     * Validates the model on a test dataset and returns accuracy metrics.
     * @param testDataset List of (text, expectedLabel) pairs to validate
     * @return ValidationResult with accuracy and misclassified samples
     */
    fun validate(testDataset: List<Pair<String, String>>): ValidationResult {
        if (!::paragraphVectors.isInitialized) {
            load()
        }
        if (!::paragraphVectors.isInitialized) {
            return ValidationResult(0.0, testDataset.size, testDataset.map { it.first to (it.second to "MODEL_NOT_LOADED") })
        }

        var correct = 0
        val misclassified = mutableListOf<Pair<String, Pair<String, String>>>()

        for ((text, expectedLabel) in testDataset) {
            val (predictedLabel, score) = predictWithScore(text)
            if (predictedLabel == expectedLabel) {
                correct++
            } else {
                misclassified.add(text to (expectedLabel to predictedLabel))
            }
        }

        val accuracy = if (testDataset.isNotEmpty()) correct.toDouble() / testDataset.size else 0.0
        return ValidationResult(accuracy, testDataset.size, misclassified)
    }

    data class ValidationResult(
        val accuracy: Double,
        val totalSamples: Int,
        val misclassified: List<Pair<String, Pair<String, String>>>
    ) {
        fun printReport() {
            println("=== Model Validation Report ===")
            println("Accuracy: ${"%.2f".format(accuracy * 100)}% (${
                (totalSamples - misclassified.size)
            }/$totalSamples)")
            if (misclassified.isNotEmpty()) {
                println("\nMisclassified samples:")
                misclassified.forEach { (text, labels) ->
                    println("  '$text' - Expected: ${labels.first}, Got: ${labels.second}")
                }
            }
        }
    }
}
