package com.ebalance.cli

import arrow.core.fold
import com.ebalance.classification.CategoryClassifierTrainer
import com.ebalance.classification.TextClassifierNeuralNetwork
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlin.io.path.inputStream

class TrainCommand : CliktCommand(name = "train") {

    private val datasetFile: String? by option("--dataset", "-d")
        .help("Path to training dataset CSV file (optional, will use classpath dataset if not provided)")

    private val modelPath: String by option("--model", "-m")
        .default("model.zip")
        .help("Path where the trained model will be saved")

    private val epochs: Int by option("--epoch", "-e")
        .int()
        .default(500)
        .help("Number of training epochs (more epochs = longer training but potentially better results)")

    private val engine: String by option("--engine")
        .default(ClassifierEngine.PARAGRAPH_VECTORS.cliName)
        .help(
            "Classifier engine to train.\n" +
            "  paragraph-vectors  DL4J ParagraphVectors (default, saves *.zip)\n" +
            "  neural-network     Built-in bag-of-words neural network (saves *.bin)"
        )

    override fun help(context: Context): String =
        "Train the category classifier.\n\n" +
        "The classifier learns from a dataset of business names mapped to category IDs.\n" +
        "The trained model is saved to the path specified by --model."

    override fun run() {
        val selectedEngine = ClassifierEngine.fromCli(engine) ?: run {
            echo("Error: Unknown engine '$engine'. Valid values: ${ClassifierEngine.cliNames()}", err = true)
            return
        }

        echo("Training category classifier...")
        echo("Engine:  ${selectedEngine.cliName}")
        echo("Model:   $modelPath")
        echo("Epochs:  $epochs")

        when (selectedEngine) {
            ClassifierEngine.PARAGRAPH_VECTORS -> trainParagraphVectors()
            ClassifierEngine.NEURAL_NETWORK -> trainNeuralNetwork()
        }
    }

    // -------------------------------------------------------------------------
    // paragraph-vectors  (DL4J — existing behaviour)
    // -------------------------------------------------------------------------

    private fun trainParagraphVectors() {
        val trainer = CategoryClassifierTrainer(modelPath = modelPath, epochs = epochs)

        val result = if (datasetFile != null) {
            echo("Dataset: $datasetFile")
            java.nio.file.Path.of(datasetFile!!).inputStream().use { inputStream ->
                trainer.trainFromStream(inputStream)
            }
        } else {
            echo("Dataset: classpath default")
            trainer.train()
        }

        result.fold(
            ifLeft = { error ->
                val message = when (error) {
                    is CategoryClassifierTrainer.TrainingError.DatasetNotFoundErr ->
                        "Dataset not found: ${error.msg}"
                    is CategoryClassifierTrainer.TrainingError.InvalidDatasetFormatErr ->
                        "Invalid dataset format: ${error.msg}"
                    is CategoryClassifierTrainer.TrainingError.TrainProcessErr ->
                        "Training failed: ${error.msg}"
                }
                echo("Error: $message", err = true)
            },
            ifRight = { success ->
                echo("""
                    |Training completed successfully!
                    |  Dataset entries: ${success.entries}
                    |  Epochs:          ${success.epochs}
                    |  Model saved to:  $modelPath
                """.trimMargin())
            }
        )
    }

    // -------------------------------------------------------------------------
    // neural-network  (built-in TextClassifierNeuralNetwork)
    // -------------------------------------------------------------------------

    private fun trainNeuralNetwork() {
        val rawText: String = if (datasetFile != null) {
            echo("Dataset: $datasetFile")
            java.io.File(datasetFile!!).readText()
        } else {
            echo("Dataset: classpath default")
            TrainCommand::class.java.getResourceAsStream("/dataset/category.for.training.csv")
                ?.bufferedReader()?.readText()
                ?: run {
                    echo("Error: Default dataset not found on classpath", err = true)
                    return
                }
        }

        val lines = rawText.lines().filter { it.isNotBlank() && it.contains(';') }

        if (lines.isEmpty()) {
            echo("Error: No valid training entries found in dataset", err = true)
            return
        }

        val labels = lines.map { it.split(";")[0].trim() }.distinct()
        // Split on whitespace runs and drop blank tokens (avoids empty-string feature pollution)
        val words  = lines.flatMap { it.split(";", limit = 2)[1].trim().lowercase()
            .split(Regex("\\s+")).filter { w -> w.isNotBlank() } }.distinct()

        // Inverse-frequency class weights to compensate for class imbalance
        val labelCounts = lines.groupingBy { it.split(";")[0].trim() }.eachCount()
        val avgCount = labelCounts.values.average()
        val classWeights = labels.associateWith { label -> avgCount / (labelCounts[label] ?: 1) }

        echo("Loaded ${lines.size} entries — labels=${labels.size}, vocab=${words.size}")
        echo("Building network: input=${words.size}, hidden=256, output=${labels.size}")

        // lambda=0.0: L1 regularization destroys input→hidden weights for sparse bag-of-words
        // inputs (word appears 1×/epoch → L1 drain >> gradient signal → weight → 0).
        // With 199 training samples, overfitting is not a concern.
        val nn = TextClassifierNeuralNetwork(words.size, 256, labels.size, lambda = 0.0, labels = labels, words = words)

        repeat(epochs) { epoch ->
            lines.forEach { line ->
                val parts = line.split(";", limit = 2)
                val label = parts[0].trim()
                // Exact word-set membership instead of substring contains
                val descWords = parts[1].trim().lowercase().split(Regex("\\s+")).toSet()
                val inputVec = DoubleArray(words.size) { i -> if (words[i] in descWords) 1.0 else 0.0 }
                val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == label) 1.0 else 0.0 }
                val weight = classWeights[label] ?: 1.0
                nn.train(inputVec, targetVec, 0.1, weight)
            }
            if ((epoch + 1) % 100 == 0 || epoch == 0) {
                echo("  Epoch ${epoch + 1}/$epochs done")
            }
        }

        val r2Score = nn.score(rawText, rawText)
        nn.saveModel(modelPath)

        echo("""
            |Training completed successfully!
            |  Dataset entries: ${lines.size}
            |  Labels:          ${labels.size}
            |  Vocabulary:      ${words.size}
            |  Epochs:          $epochs
            |  R² score:        ${"%.4f".format(r2Score)} (train set; 1.0 = perfect)
            |  Model saved to:  $modelPath
        """.trimMargin())
    }
}
