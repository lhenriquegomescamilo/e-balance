package com.ebalance.cli

import arrow.core.fold
import com.ebalance.classification.CategoryClassifierTrainer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
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

    override fun help(context: Context): String = 
        "Train the category classifier.\n\n" +
        "The classifier learns from a dataset of business names mapped to category IDs.\n" +
        "The model is saved to model.zip and used during transaction import."

    override fun run() {
        echo("Training category classifier...")
        
        val trainer = CategoryClassifierTrainer(modelPath = modelPath, epochs = epochs)
        
        val result = if (datasetFile != null) {
            echo("Using dataset file: $datasetFile with $epochs epochs")
            java.nio.file.Path.of(datasetFile!!).inputStream().use { inputStream ->
                trainer.trainFromStream(inputStream)
            }
        } else {
            echo("Using default dataset from classpath")
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
                    |  Epochs:         ${success.epochs}
                    |  Model saved to:  $modelPath
                """.trimMargin())
            }
        )
    }
}
