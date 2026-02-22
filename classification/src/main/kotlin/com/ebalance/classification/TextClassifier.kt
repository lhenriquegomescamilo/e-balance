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

class TextClassifier(
    private val learningRate: Double = 0.025,
    private val minLearningRate: Double = 0.001,
    private val epochs: Int = 1000,
    private val layerSize: Int = 100,
    private val modelPath: String = "model.zip"
) {

    private val businessStopWords = setOf(
        "lda", "l d a", "Lda", "Unipessoal", "unipessoal", "sa", "s.a.", "s a", "limitada", "sociedade",
        "portugal", "portuguesa", "e", "de", "da", "do", "das", "dos", "com",
        "comunicacoes", "actividades", "gestao", "administracao", "servicos"
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
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation/symbols
            .split(" ")
            .filter { it !in businessStopWords && it.isNotBlank() }
            .joinToString(" ")
            .filter { it.isLetter() }
            .removeAccents()
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
        // 1. Pre-clean the dataset
        this.cleanedTrainingData = dataset.map { cleanText(it.first) to it.second }

        val iterator = SimpleIterator(cleanedTrainingData)

        paragraphVectors = ParagraphVectors.Builder()
            .learningRate(learningRate)
            .minLearningRate(minLearningRate)
            .batchSize(cleanedTrainingData.size)
            .epochs(epochs) // Increased epochs slightly for better stability on small data
            .layerSize(layerSize)
            .iterate(iterator)
            .trainWordVectors(true)
            .tokenizerFactory(tokenizerFactory)
            .stopWords(businessStopWords.toList())
            .build()

        paragraphVectors.fit()

        // Persist the trained model
        runCatching {
            WordVectorSerializer.writeParagraphVectors(paragraphVectors, File(modelPath))
        }.onFailure { it.printStackTrace() }
    }

    fun load() {
        val f = File(modelPath)
        if (f.exists()) {
            runCatching {
                WordVectorSerializer.readParagraphVectors(f).also {
                    it.tokenizerFactory = tokenizerFactory
                    paragraphVectors = it
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    fun predictWithScore(
        text: String,
        unknown: Pair<String, Double> = "0" to 0.0,
        aiThreshold: Double = 0.70
    ): Pair<String, Double> {

        // Ensure model is loaded
        if (!::paragraphVectors.isInitialized) {
            load()
        }
        if (!::paragraphVectors.isInitialized) return unknown

        val cleanedInput = cleanText(text)

        // 1. Check if model knows the words
        val tokens = tokenizerFactory.create(cleanedInput).tokens
        val knownWords = tokens.filter { paragraphVectors.vocab().containsWord(it) }

        var aiLabel = "0"
        var aiScore = 0.0

        if (knownWords.isNotEmpty()) {
            aiLabel = paragraphVectors.nearestLabels(cleanedInput, 1).firstOrNull() ?: "0"
            aiScore = runCatching {
                paragraphVectors.similarityToLabel(cleanedInput, aiLabel)
            }.getOrDefault(0.0)
        }

        // 2. Fallback to Fuzzy Matching if AI is unconfident or words are unknown
        if (aiScore < aiThreshold) {
            val fuzzyMatch = findBestFuzzyMatch(cleanedInput)
            if (fuzzyMatch != null && fuzzyMatch.second > 0.70) { // 70% string similarity required
                return fuzzyMatch
            }
        }

        return if (aiScore >= aiThreshold) aiLabel to aiScore else unknown
    }

    private fun findBestFuzzyMatch(text: String): Pair<String, Double>? {
        if (cleanedTrainingData.isEmpty() || text.isEmpty()) return null

        return cleanedTrainingData.map { (trainedName, label) ->
            val distance = levenshtein.apply(text, trainedName)
            val maxLength = maxOf(text.length, trainedName.length)
            val similarity = if (maxLength == 0) 0.0 else (1.0 - (distance.toDouble() / maxLength))
            label to similarity
        }.maxByOrNull { it.second }
    }
}
