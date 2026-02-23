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
    private val learningRate: Double = 0.025, // Reduzido para maior estabilidade
    private val minLearningRate: Double = 0.001,
    private val epochs: Int = 500, // Ajustado para ParagraphVectors (iterações internas)
    private val layerSize: Int = 100, // Reduzido para evitar overfitting em frases curtas
    private val windowSize: Int = 5,
    private val modelPath: String = "model.zip"
) {

    private val log = LoggerFactory.getLogger(TextClassifier::class.java)

    // Stop words simplificadas: apenas ruído real, sem remover nomes de marcas ou setores
    private val businessStopWords = setOf(
        "lda", "l d a", "Lda", "Unipessoal", "unipessoal", "sa", "s.a.", "s a", "limitada", "sociedade",
        "portugal", "portuguesa", "e", "de", "da", "do", "das", "dos", "com",
        "comunicacoes", "actividades", "gestao", "administracao", "servicos",
        "lda", "unipessoal", "sa", "limitada", "sociedade", "e", "de", "da", "do", "das", "dos", "com", "a", "o",
        "56413 Cc Mar"
    )

    private lateinit var paragraphVectors: ParagraphVectors
    private val tokenizerFactory = DefaultTokenizerFactory().apply {
        tokenPreProcessor = CommonPreprocessor()
    }

    private var cleanedTrainingData: List<Pair<String, String>> = listOf()
    private val levenshtein = LevenshteinDistance()

    /**
     * Limpa o texto focando na marca.
     * REMOVE números (como datas de transação) que confundem o modelo.
     */
    private fun cleanText(text: String): String {
        return text.lowercase()
            .removeAccents()
            .replace(Regex("\\d+"), " ") // Remove números (datas/valores)
            .replace(Regex("[^a-z\\s]"), " ") // Remove símbolos
            .split(" ")
            .filter { it !in businessStopWords && it.length > 1 }
            .joinToString(" ")
            .trim()
            .replace(Regex("\\s+"), " ") // Normaliza espaços
    }

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
        override fun reset() { index = 0 }
        override fun finish() {}
        override fun getPreProcessor(): SentencePreProcessor? = preProcessor
        override fun setPreProcessor(p0: SentencePreProcessor?) { this.preProcessor = p0 }
    }

    fun train(dataset: List<Pair<String, String>>) {
        log.info("Starting model training with ${dataset.size} samples")

        // 1. Limpeza e Oversampling
        // Categorias pequenas precisam ser repetidas para o modelo não as ignorar
        val rawCleaned = dataset.map { cleanText(it.first) to it.second }
        val categoryGroups = rawCleaned.groupBy { it.second }

        val augmentedData = mutableListOf<Pair<String, String>>()
        categoryGroups.forEach { (label, samples) ->
            // Se a categoria tem poucos exemplos, repetimos até ter um mínimo de 15
            val repeatFactor = when {
                samples.size < 5 -> 5
                samples.size < 15 -> 2
                else -> 1
            }
            repeat(repeatFactor) { augmentedData.addAll(samples) }
        }

        this.cleanedTrainingData = rawCleaned // Mantemos os originais para o Fuzzy Match
        val iterator = SimpleIterator(augmentedData)

        paragraphVectors = ParagraphVectors.Builder()
            .learningRate(learningRate)
            .minLearningRate(minLearningRate)
            .batchSize(64)
            .epochs(epochs)
            .layerSize(layerSize)
            .windowSize(windowSize)
            .iterate(iterator)
            .trainWordVectors(true)
            .tokenizerFactory(tokenizerFactory)
            .sampling(0.0) // Desligado para dataset pequeno
            .minWordFrequency(1) // Importante: nomes de lojas podem ser únicos
            .seed(42L)
            .build()

        log.debug("Fitting ParagraphVectors model...")
        paragraphVectors.fit()

        runCatching {
            WordVectorSerializer.writeParagraphVectors(paragraphVectors, File(modelPath))
            log.info("Model saved successfully")
        }.onFailure { log.error("Error saving model", it) }
    }

    fun load() {
        val f = File(modelPath)
        if (f.exists()) {
            runCatching {
                paragraphVectors = WordVectorSerializer.readParagraphVectors(f).also {
                    it.tokenizerFactory = tokenizerFactory
                }
                log.info("Model loaded")
            }
        }
    }

    fun isModelLoaded(): Boolean = ::paragraphVectors.isInitialized

    fun predictWithScore(
        text: String,
        unknown: Pair<String, Double> = "DESCONHECIDA" to 0.0,
        aiThreshold: Double = 0.45 // Threshold ligeiramente menor para ParagraphVectors
    ): Pair<String, Double> {

        if (!::paragraphVectors.isInitialized) load()
        if (!::paragraphVectors.isInitialized) return unknown

        val cleanedInput = cleanText(text)
        if (cleanedInput.isBlank()) return unknown

        // 1. Predição da Rede Neural
        val tokens = tokenizerFactory.create(cleanedInput).tokens
        val knownWords = tokens.filter { paragraphVectors.vocab().containsWord(it) }

        var aiLabel = "0"
        var aiScore = 0.0

        if (knownWords.isNotEmpty()) {
            // Obtém o rótulo mais próximo semanticamente
            aiLabel = paragraphVectors.nearestLabels(cleanedInput, 1).firstOrNull() ?: "0"
            aiScore = runCatching {
                paragraphVectors.similarityToLabel(cleanedInput, aiLabel)
            }.getOrDefault(0.0)
        }

        // 2. Fallback: Fuzzy Matching
        // Se a IA falhar ou o score for baixo, o Fuzzy Match é excelente para "Mcdonalds" vs "Macdonalds"
        if (aiScore < aiThreshold) {
            val fuzzyMatch = findBestFuzzyMatch(cleanedInput)
            if (fuzzyMatch != null && fuzzyMatch.second > 0.60) {
                return fuzzyMatch
            }
        }

        return if (aiScore >= aiThreshold) aiLabel to aiScore else unknown
    }

    private fun findBestFuzzyMatch(text: String): Pair<String, Double>? {
        if (cleanedTrainingData.isEmpty() || text.isEmpty()) return null

        // Compara a entrada com todo o dataset de treino limpo
        return cleanedTrainingData.map { (trainedName, label) ->
            val distance = levenshtein.apply(text, trainedName)
            val maxLength = maxOf(text.length, trainedName.length)
            val similarity = if (maxLength == 0) 0.0 else (1.0 - (distance.toDouble() / maxLength))
            label to similarity
        }.maxByOrNull { it.second }
    }

    fun validate(testDataset: List<Pair<String, String>>): ValidationResult {
        if (!::paragraphVectors.isInitialized) load()
        if (!::paragraphVectors.isInitialized) {
            return ValidationResult(0.0, testDataset.size, testDataset.map { it.first to (it.second to "ERROR") })
        }

        var correct = 0
        val misclassified = mutableListOf<Pair<String, Pair<String, String>>>()

        for ((text, expectedLabel) in testDataset) {
            val (predictedLabel, _) = predictWithScore(text)
            if (predictedLabel == expectedLabel) {
                correct++
            } else {
                misclassified.add(text to (expectedLabel to predictedLabel))
            }
        }

        return ValidationResult(correct.toDouble() / testDataset.size, testDataset.size, misclassified)
    }

    data class ValidationResult(
        val accuracy: Double,
        val totalSamples: Int,
        val misclassified: List<Pair<String, Pair<String, String>>>
    ) {
        fun printReport() {
            println("=== Validation: ${"%.2f".format(accuracy * 100)}% Accuracy ===")
            if (misclassified.isNotEmpty()) {
                println("Top errors:")
                misclassified.take(10).forEach { (txt, labels) ->
                    println("  '$txt': Expected ${labels.first} but got ${labels.second}")
                }
            }
        }
    }
}