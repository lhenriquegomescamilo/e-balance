package com.ebalance.cli

/**
 * Supported classifier engines for the `--engine` option on [ImportCommand] and [TrainCommand].
 *
 * | CLI value            | Description                                         |
 * |----------------------|-----------------------------------------------------|
 * | `paragraph-vectors`  | DL4J ParagraphVectors (default); model file: *.zip  |
 * | `neural-network`     | Built-in bag-of-words neural network; file: *.bin   |
 */
enum class ClassifierEngine(val cliName: String) {
    PARAGRAPH_VECTORS("paragraph-vectors"),
    NEURAL_NETWORK("neural-network");

    companion object {
        fun fromCli(value: String): ClassifierEngine? =
            entries.firstOrNull { it.cliName.equals(value, ignoreCase = true) }

        fun cliNames(): String = entries.joinToString(", ") { it.cliName }
    }
}
