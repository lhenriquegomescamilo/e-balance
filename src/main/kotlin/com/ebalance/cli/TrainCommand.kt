package com.ebalance.cli

import arrow.core.fold
import com.ebalance.classification.CategoryClassifierTrainer
import com.ebalance.classification.MetalNeuralNetwork
import com.ebalance.classification.TextClassifierNeuralNetwork
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
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

    private val useGpu: Boolean by option("--gpu")
        .flag("--no-gpu", default = false)
        .help(
            "Use Apple Metal GPU for training (neural-network engine only).\n" +
            "Requires libmetal_bridge.dylib on java.library.path.\n" +
            "Build once with: ./gradlew :metal-bridge:linkReleaseSharedMacosArm64\n" +
            "Then install:    ./gradlew installDistWithMetal"
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

        if (useGpu && selectedEngine != ClassifierEngine.NEURAL_NETWORK) {
            echo("Error: --gpu is only supported with --engine neural-network", err = true)
            return
        }

        echo("Training category classifier...")
        echo("Engine:  ${selectedEngine.cliName}")
        echo("Model:   $modelPath")
        echo("Epochs:  $epochs")
        if (useGpu) echo("Backend: Metal GPU (Apple Silicon)")

        when (selectedEngine) {
            ClassifierEngine.PARAGRAPH_VECTORS -> trainParagraphVectors()
            ClassifierEngine.NEURAL_NETWORK    -> trainNeuralNetwork()
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
    // neural-network  —  dispatches to CPU or GPU path based on --gpu flag
    // -------------------------------------------------------------------------

    private fun trainNeuralNetwork() {
        val rawText: String = if (datasetFile != null) {
            echo("Dataset: $datasetFile")
            File(datasetFile!!).readText()
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
        val words  = lines.flatMap { it.split(";", limit = 2)[1].trim().lowercase()
            .split(Regex("\\s+")).filter { w -> w.isNotBlank() } }.distinct()

        val labelCounts  = lines.groupingBy { it.split(";")[0].trim() }.eachCount()
        val avgCount     = labelCounts.values.average()
        val classWeights = labels.associateWith { label -> avgCount / (labelCounts[label] ?: 1) }

        echo("Loaded ${lines.size} entries — labels=${labels.size}, vocab=${words.size}")
        echo("Building network: input=${words.size}, hidden=256, output=${labels.size}")

        if (useGpu) trainOnGpu(rawText, lines, labels, words, classWeights)
        else        trainOnCpu(rawText, lines, labels, words, classWeights)
    }

    // ── CPU path ─────────────────────────────────────────────────────────────

    private fun trainOnCpu(
        rawText: String,
        lines: List<String>,
        labels: List<String>,
        words: List<String>,
        classWeights: Map<String, Double>
    ) {
        // lambda=0.0: L1 regularization destroys input→hidden weights for sparse
        // bag-of-words inputs (word appears 1×/epoch → L1 drain >> gradient signal).
        val nn = TextClassifierNeuralNetwork(
            inputSize  = words.size,
            hiddenSize = 256,
            outputSize = labels.size,
            lambda     = 0.0,
            labels     = labels,
            words      = words
        )

        repeat(epochs) { epoch ->
            lines.forEach { line ->
                val parts     = line.split(";", limit = 2)
                val label     = parts[0].trim()
                val descWords = parts[1].trim().lowercase().split(Regex("\\s+")).toSet()
                val inputVec  = DoubleArray(words.size)  { i -> if (words[i]  in descWords) 1.0 else 0.0 }
                val targetVec = DoubleArray(labels.size) { i -> if (labels[i] == label)     1.0 else 0.0 }
                nn.train(inputVec, targetVec, 0.1, classWeights[label] ?: 1.0)
            }
            if ((epoch + 1) % 100 == 0 || epoch == 0) echo("  Epoch ${epoch + 1}/$epochs done")
        }

        val r2Score = nn.score(rawText, rawText)
        nn.saveModel(modelPath)
        writeMetaSidecar("cpu")

        echo("""
            |Training completed successfully!
            |  Backend:          CPU
            |  Dataset entries: ${lines.size}
            |  Labels:          ${labels.size}
            |  Vocabulary:      ${words.size}
            |  Epochs:          $epochs
            |  R² score:        ${"%.4f".format(r2Score)} (train set; 1.0 = perfect)
            |  Model saved to:  $modelPath
        """.trimMargin())
    }

    // ── GPU path ─────────────────────────────────────────────────────────────

    private fun trainOnGpu(
        rawText: String,
        lines: List<String>,
        labels: List<String>,
        words: List<String>,
        classWeights: Map<String, Double>
    ) {
        // Pre-build the full-batch FloatArray matrices once outside the epoch loop.
        // MetalNeuralNetwork expects flattened row-major layout: [batch × features].
        val inputs  = FloatArray(lines.size * words.size)
        val targets = FloatArray(lines.size * labels.size)
        val weights = FloatArray(lines.size)

        lines.forEachIndexed { i, line ->
            val parts     = line.split(";", limit = 2)
            val label     = parts[0].trim()
            val descWords = parts[1].trim().lowercase().split(Regex("\\s+")).toSet()
            words.forEachIndexed  { j, w -> inputs [i * words.size  + j] = if (w in descWords) 1f else 0f }
            labels.forEachIndexed { j, l -> targets[i * labels.size + j] = if (l == label)     1f else 0f }
            weights[i] = (classWeights[label] ?: 1.0).toFloat()
        }

        try {
            MetalNeuralNetwork(
                inputSize  = words.size,
                hiddenSize = 256,
                outputSize = labels.size,
                maxBatch   = lines.size,
                labels     = labels,
                words      = words
            ).use { nn ->
                repeat(epochs) { epoch ->
                    nn.trainBatch(inputs, targets, weights, lines.size, 0.1f)
                    if ((epoch + 1) % 100 == 0 || epoch == 0) echo("  Epoch ${epoch + 1}/$epochs done")
                }

                nn.saveModel(modelPath)
                writeMetaSidecar("gpu")

                echo("""
                    |Training completed successfully!
                    |  Backend:          Metal GPU
                    |  Dataset entries: ${lines.size}
                    |  Labels:          ${labels.size}
                    |  Vocabulary:      ${words.size}
                    |  Epochs:          $epochs
                    |  Model saved to:  $modelPath
                """.trimMargin())
            }
        } catch (e: UnsatisfiedLinkError) {
            echo("Error: Metal GPU bridge not found — ${e.message}", err = true)
            echo("Build it first:", err = true)
            echo("  ./gradlew :metal-bridge:linkReleaseSharedMacosArm64", err = true)
            echo("Then run via:", err = true)
            echo("  ./gradlew installDistWithMetal   (copies dylib into distribution)", err = true)
            echo("  or pass -Djava.library.path=metal-bridge/build/bin/macosArm64/releaseShared", err = true)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Writes a sidecar file next to the model so ImportCommand can show the training backend. */
    private fun writeMetaSidecar(backend: String) {
        File("$modelPath.meta").writeText("training.backend=$backend\ntraining.epochs=$epochs\n")
    }
}
