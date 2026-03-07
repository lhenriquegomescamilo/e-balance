package com.ebalance.classification

import org.slf4j.LoggerFactory
import java.io.File

class NeuralNetworkClassifier(val modelPath: String = "nn-model.bin") {

    private val log = LoggerFactory.getLogger(NeuralNetworkClassifier::class.java)
    private var network: TextClassifierNeuralNetwork? = null

    fun isModelLoaded(): Boolean =
        network != null && network!!.labels.isNotEmpty() && network!!.words.isNotEmpty()

    fun load() {
        val file = File(modelPath)
        if (!file.exists()) {
            log.warn("Model file not found: $modelPath")
            return
        }
        network = TextClassifierNeuralNetwork.loadModel(modelPath)
        log.info("Model loaded from $modelPath — labels=${network!!.labels.size}, vocab=${network!!.words.size}")
    }

    fun predict(text: String): Pair<String, Double> {
        if (!isModelLoaded()) load()
        val nn = network ?: return UNKNOWN
        val words = nn.words
        val textWords = text.lowercase().split(Regex("\\s+")).toSet()
        val inputVec = DoubleArray(words.size) { i -> if (words[i] in textWords) 1.0 else 0.0 }
        val output = nn.predict(inputVec)
        val bestIdx = output.indices.maxByOrNull { output[it] } ?: 0
        return nn.labels[bestIdx] to output[bestIdx]
    }

    companion object {
        val UNKNOWN = "DESCONHECIDA" to 0.0
    }
}
