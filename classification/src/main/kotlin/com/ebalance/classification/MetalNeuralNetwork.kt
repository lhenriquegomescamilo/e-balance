package com.ebalance.classification

import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.BufferedOutputStream

/**
 * A Metal-GPU-accelerated neural network that is API-compatible with
 * [TextClassifierNeuralNetwork] for the training path.
 *
 * The actual forward/backward/weight-update logic runs entirely on the Apple
 * GPU via the Kotlin/Native [metal-bridge] shared library
 * (`libmetal_bridge.dylib`).  All weight matrices live in Metal shared-memory
 * buffers; on Apple Silicon the CPU and GPU share the same physical DRAM, so
 * "upload" and "download" are just pointer re-use — not real copies.
 *
 * ## Float32 vs Float64
 * Metal does not support 64-bit floats.  This class uses [FloatArray] /
 * [Float] internally.  The [TextClassifierNeuralNetwork] uses [DoubleArray] /
 * [Double].  For a bag-of-words text classifier the precision difference is
 * imperceptible.
 *
 * ## Usage
 * ```kotlin
 * val nn = MetalNeuralNetwork(vocabSize, hiddenSize = 256, outputSize = 47,
 *                              maxBatch = 224, labels = labels, words = words)
 * repeat(epochs) {
 *     nn.trainBatch(inputs, targets, classWeights, batchSize, lr = 0.1f)
 * }
 * val probs = nn.predict(singleInput)   // FloatArray, softmax probabilities
 * nn.saveModel("nn-model.bin")
 * ```
 *
 * @param inputSize   vocabulary size (bag-of-words feature dimension)
 * @param hiddenSize  number of hidden units
 * @param outputSize  number of output classes
 * @param maxBatch    maximum training batch size (pre-allocates GPU intermediates)
 * @param labels      ordered list of class names (required for [saveModel])
 * @param words       ordered vocabulary list (required for [saveModel])
 */
class MetalNeuralNetwork(
    val inputSize:  Int,
    val hiddenSize: Int,
    val outputSize: Int,
    val maxBatch:   Int,
    val labels: List<String> = emptyList(),
    val words:  List<String> = emptyList()
) : AutoCloseable {

    // Opaque handle to the Kotlin/Native MetalContext object.
    private val ctx: Long = createContext(inputSize, hiddenSize, outputSize, maxBatch)

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    /**
     * Runs one full-batch forward + backward pass on the GPU and updates
     * the weights immediately (mini-batch / full-batch gradient descent).
     *
     * @param inputs       flattened [batchSize × inputSize] bag-of-words matrix
     * @param targets      flattened [batchSize × outputSize] one-hot label matrix
     * @param classWeights per-sample importance weights for class-imbalance compensation
     * @param batchSize    number of samples in this batch (≤ [maxBatch])
     * @param lr           learning rate
     */
    fun trainBatch(
        inputs:       FloatArray,
        targets:      FloatArray,
        classWeights: FloatArray,
        batchSize:    Int,
        lr:           Float
    ) = trainBatch(ctx, inputs, targets, classWeights, batchSize, lr)

    // -----------------------------------------------------------------------
    // Inference
    // -----------------------------------------------------------------------

    /**
     * Runs a forward pass for a single sample.
     *
     * @param input bag-of-words vector of size [inputSize]
     * @return softmax probability distribution over [outputSize] classes
     */
    fun predict(input: FloatArray): FloatArray = predict(ctx, input)

    // -----------------------------------------------------------------------
    // Persistence — binary format compatible with TextClassifierNeuralNetwork
    // -----------------------------------------------------------------------

    /**
     * Saves the model to [path] in the same binary format used by
     * [TextClassifierNeuralNetwork.saveModel], so a saved Metal model can be
     * loaded by the CPU implementation for inference if needed.
     *
     * Format:
     *   MODEL_VERSION (int32) | label count (int32) | labels (UTF) |
     *   word count (int32) | words (UTF) | inputSize | hiddenSize | outputSize |
     *   lambda (double=0) | weightsIH | weightsHO | biasH | biasO
     */
    fun saveModel(path: String) {
        val wIH = getWeightsIH(ctx)
        val wHO = getWeightsHO(ctx)
        val bH  = getBiasH(ctx)
        val bO  = getBiasO(ctx)

        DataOutputStream(BufferedOutputStream(FileOutputStream(path))).use { out ->
            out.writeInt(TextClassifierNeuralNetwork.MODEL_VERSION)
            out.writeInt(labels.size)
            labels.forEach { out.writeUTF(it) }
            out.writeInt(words.size)
            words.forEach { out.writeUTF(it) }
            // Network dimensions (must match TextClassifierNeuralNetwork's load format)
            out.writeInt(inputSize)
            out.writeInt(hiddenSize)
            out.writeInt(outputSize)
            out.writeDouble(0.0)   // lambda — always 0 (no L1 on Metal path)
            // Weights serialised as doubles to stay binary-compatible
            wIH.forEach { out.writeDouble(it.toDouble()) }
            wHO.forEach { out.writeDouble(it.toDouble()) }
            bH.forEach  { out.writeDouble(it.toDouble()) }
            bO.forEach  { out.writeDouble(it.toDouble()) }
        }
    }

    override fun close() = destroyContext(ctx)

    // -----------------------------------------------------------------------
    // JNI declarations — implemented in libmetal_bridge.dylib
    // -----------------------------------------------------------------------
    companion object {
        init {
            System.loadLibrary("metal_bridge")
        }

        @JvmStatic private external fun createContext(
            inputSize: Int, hiddenSize: Int, outputSize: Int, maxBatch: Int
        ): Long

        @JvmStatic private external fun trainBatch(
            ctx: Long,
            inputs: FloatArray, targets: FloatArray, classWeights: FloatArray,
            batchSize: Int, lr: Float
        )

        @JvmStatic private external fun predict(ctx: Long, input: FloatArray): FloatArray

        @JvmStatic private external fun getWeightsIH(ctx: Long): FloatArray
        @JvmStatic private external fun getWeightsHO(ctx: Long): FloatArray
        @JvmStatic private external fun getBiasH(ctx: Long): FloatArray
        @JvmStatic private external fun getBiasO(ctx: Long): FloatArray

        @JvmStatic private external fun destroyContext(ctx: Long)
    }
}
