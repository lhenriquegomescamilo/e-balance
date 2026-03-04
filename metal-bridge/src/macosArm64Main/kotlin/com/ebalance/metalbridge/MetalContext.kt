@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ebalance.metalbridge

import kotlinx.cinterop.*
import platform.Metal.*
import platform.Foundation.*

/**
 * Holds the Metal device, compiled shader pipelines, and all GPU-side buffers
 * for a single neural network instance.
 *
 * All weight matrices and intermediate activations live in MTLBuffers backed by
 * Apple's Unified Memory — the CPU can read/write them without any explicit copy.
 *
 * @param inputSize   vocabulary size  (number of bag-of-words features)
 * @param hiddenSize  hidden layer width
 * @param outputSize  number of output classes
 * @param maxBatch    maximum number of training samples per batch (pre-allocates intermediates)
 */
internal class MetalContext(
    val inputSize: Int,
    val hiddenSize: Int,
    val outputSize: Int,
    val maxBatch: Int
) {
    // -----------------------------------------------------------------------
    // Device & command queue
    // -----------------------------------------------------------------------
    private val device: MTLDeviceProtocol =
        MTLCreateSystemDefaultDevice() ?: error("No Metal device found on this machine")

    private val queue: MTLCommandQueueProtocol =
        device.newCommandQueue() ?: error("Failed to create Metal command queue")

    // -----------------------------------------------------------------------
    // Compiled pipeline states (one per MSL kernel)
    // -----------------------------------------------------------------------
    private val psMatmul:        MTLComputePipelineStateProtocol
    private val psMatmulAt:      MTLComputePipelineStateProtocol
    private val psMatmulBt:      MTLComputePipelineStateProtocol
    private val psAddBias:       MTLComputePipelineStateProtocol
    private val psSigmoid:       MTLComputePipelineStateProtocol
    private val psSoftmax:       MTLComputePipelineStateProtocol
    private val psOutputGrad:    MTLComputePipelineStateProtocol
    private val psHiddenGrad:    MTLComputePipelineStateProtocol
    private val psAddInplace:    MTLComputePipelineStateProtocol
    private val psScaleInplace:  MTLComputePipelineStateProtocol
    private val psReduceSumCols: MTLComputePipelineStateProtocol

    // -----------------------------------------------------------------------
    // Persistent weight buffers (survive across training steps)
    // -----------------------------------------------------------------------
    val wIH: MTLBufferProtocol   // [inputSize  × hiddenSize]  input→hidden weights
    val wHO: MTLBufferProtocol   // [hiddenSize × outputSize]  hidden→output weights
    val bH:  MTLBufferProtocol   // [hiddenSize]               hidden bias
    val bO:  MTLBufferProtocol   // [outputSize]               output bias

    // -----------------------------------------------------------------------
    // Pre-allocated intermediate buffers (avoid per-step allocation overhead)
    // -----------------------------------------------------------------------
    private val hidden:      MTLBufferProtocol   // [maxBatch × hiddenSize]
    private val output:      MTLBufferProtocol   // [maxBatch × outputSize]
    private val outGrad:     MTLBufferProtocol   // [maxBatch × outputSize]
    private val hiddenErr:   MTLBufferProtocol   // [maxBatch × hiddenSize]
    private val hiddenGrad:  MTLBufferProtocol   // [maxBatch × hiddenSize]
    private val dWIH:        MTLBufferProtocol   // [inputSize  × hiddenSize]
    private val dWHO:        MTLBufferProtocol   // [hiddenSize × outputSize]
    private val dBH:         MTLBufferProtocol   // [hiddenSize]
    private val dBO:         MTLBufferProtocol   // [outputSize]

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------
    init {
        // Compile all shaders from the embedded MSL source string
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val lib = device.newLibraryWithSource(NN_SHADERS, null, err.ptr)
                ?: error("Metal shader compile failed: ${err.value?.localizedDescription}")

            fun pipeline(name: String): MTLComputePipelineStateProtocol {
                val fn = lib.newFunctionWithName(name)
                    ?: error("Shader function not found: '$name'")
                return device.newComputePipelineStateWithFunction(fn, err.ptr)
                    ?: error("Pipeline creation failed for '$name': ${err.value?.localizedDescription}")
            }

            psMatmul        = pipeline("matmul")
            psMatmulAt      = pipeline("matmul_at")
            psMatmulBt      = pipeline("matmul_bt")
            psAddBias       = pipeline("add_bias")
            psSigmoid       = pipeline("sigmoid_inplace")
            psSoftmax       = pipeline("softmax_rows")
            psOutputGrad    = pipeline("output_gradient")
            psHiddenGrad    = pipeline("hidden_gradient")
            psAddInplace    = pipeline("add_inplace")
            psScaleInplace  = pipeline("scale_inplace")
            psReduceSumCols = pipeline("reduce_sum_cols")
        }

        // Weight buffers — small random Gaussian initialisation (σ = 0.1)
        wIH = randomBuffer(inputSize * hiddenSize)
        wHO = randomBuffer(hiddenSize * outputSize)
        bH  = zeroBuffer(hiddenSize)
        bO  = zeroBuffer(outputSize)

        // Intermediate buffers
        hidden     = zeroBuffer(maxBatch * hiddenSize)
        output     = zeroBuffer(maxBatch * outputSize)
        outGrad    = zeroBuffer(maxBatch * outputSize)
        hiddenErr  = zeroBuffer(maxBatch * hiddenSize)
        hiddenGrad = zeroBuffer(maxBatch * hiddenSize)
        dWIH       = zeroBuffer(inputSize  * hiddenSize)
        dWHO       = zeroBuffer(hiddenSize * outputSize)
        dBH        = zeroBuffer(hiddenSize)
        dBO        = zeroBuffer(outputSize)
    }

    // -----------------------------------------------------------------------
    // Training — one full batch forward + backward pass on the GPU
    //
    //  inputs       : flattened [batch × inputSize]  FloatArray
    //  targets      : flattened [batch × outputSize] FloatArray
    //  classWeights : [batch]                        FloatArray  (one weight per sample)
    //  batch        : number of samples (≤ maxBatch)
    //  lr           : learning rate
    // -----------------------------------------------------------------------
    fun trainBatch(
        inputs: FloatArray,
        targets: FloatArray,
        classWeights: FloatArray,
        batch: Int,
        lr: Float
    ) {
        require(batch <= maxBatch) { "batch=$batch exceeds maxBatch=$maxBatch" }

        // Upload input data into shared-memory MTLBuffers (zero-copy on Apple Silicon)
        val inputBuf  = sharedBuffer(inputs)
        val targetBuf = sharedBuffer(targets)
        val cwBuf     = sharedBuffer(classWeights)

        val cb = queue.commandBuffer() ?: error("Failed to create Metal command buffer")
        val enc = cb.computeCommandEncoder() ?: error("Failed to create compute encoder")

        // ── FORWARD PASS ────────────────────────────────────────────────────

        // hidden_pre = inputs × wIH          [batch × inputSize] × [inputSize × hiddenSize]
        encodeMatmul(enc, inputBuf, wIH, hidden, batch, inputSize, hiddenSize)
        // hidden_pre += bH
        encodeAddBias(enc, hidden, bH, batch, hiddenSize)
        // hidden = sigmoid(hidden_pre)
        encodeSigmoid(enc, hidden, batch * hiddenSize)

        // output_pre = hidden × wHO          [batch × hiddenSize] × [hiddenSize × outputSize]
        encodeMatmul(enc, hidden, wHO, output, batch, hiddenSize, outputSize)
        // output_pre += bO
        encodeAddBias(enc, output, bO, batch, outputSize)
        // output = softmax(output_pre)  per-row
        encodeSoftmax(enc, output, batch, outputSize)

        // ── BACKWARD PASS ───────────────────────────────────────────────────

        // outGrad[i][j] = (target[i][j] - output[i][j]) * classWeight[i]
        encodeOutputGradient(enc, output, targetBuf, cwBuf, outGrad, batch, outputSize)

        // hiddenErr = outGrad × wHOᵀ         [batch × outputSize] × [outputSize × hiddenSize]
        encodeMatmulBt(enc, outGrad, wHO, hiddenErr, batch, outputSize, hiddenSize)

        // hiddenGrad[i] = hiddenErr[i] * sigmoid_deriv(hidden[i])
        encodeHiddenGradient(enc, hiddenErr, hidden, hiddenGrad, batch * hiddenSize)

        // ── WEIGHT UPDATES ──────────────────────────────────────────────────

        // dWHO = hiddenᵀ × outGrad           [hiddenSize × batch] × [batch × outputSize]
        encodeMatmulAt(enc, hidden, outGrad, dWHO, batch, hiddenSize, outputSize)
        // wHO += lr * dWHO
        encodeScaleAndAdd(enc, wHO, dWHO, lr, hiddenSize * outputSize)

        // dWIH = inputsᵀ × hiddenGrad        [inputSize × batch] × [batch × hiddenSize]
        encodeMatmulAt(enc, inputBuf, hiddenGrad, dWIH, batch, inputSize, hiddenSize)
        // wIH += lr * dWIH
        encodeScaleAndAdd(enc, wIH, dWIH, lr, inputSize * hiddenSize)

        // Bias updates: sum gradients over the batch dimension
        encodeReduceAndAdd(enc, outGrad,    dBO, bO, batch, outputSize, lr)
        encodeReduceAndAdd(enc, hiddenGrad, dBH, bH, batch, hiddenSize, lr)

        enc.endEncoding()
        cb.commit()
        cb.waitUntilCompleted()
    }

    // -----------------------------------------------------------------------
    // Inference — single sample forward pass, returns softmax probabilities
    // -----------------------------------------------------------------------
    fun predict(input: FloatArray): FloatArray {
        require(input.size == inputSize)
        val inputBuf = sharedBuffer(input)
        val outBuf   = zeroBuffer(outputSize)

        val cb  = queue.commandBuffer() ?: error("Failed to create command buffer")
        val enc = cb.computeCommandEncoder() ?: error("Failed to create encoder")

        // hidden_pre = input × wIH  (batch=1)
        encodeMatmul(enc, inputBuf, wIH, hidden, 1, inputSize, hiddenSize)
        encodeAddBias(enc, hidden, bH, 1, hiddenSize)
        encodeSigmoid(enc, hidden, hiddenSize)

        // output_pre = hidden × wHO  (batch=1)
        encodeMatmul(enc, hidden, wHO, outBuf, 1, hiddenSize, outputSize)
        encodeAddBias(enc, outBuf, bO, 1, outputSize)
        encodeSoftmax(enc, outBuf, 1, outputSize)

        enc.endEncoding()
        cb.commit()
        cb.waitUntilCompleted()

        return readBuffer(outBuf, outputSize)
    }

    // -----------------------------------------------------------------------
    // Weight accessors (for saving the model back to JVM)
    // -----------------------------------------------------------------------
    fun readWeightsIH() = readBuffer(wIH, inputSize * hiddenSize)
    fun readWeightsHO() = readBuffer(wHO, hiddenSize * outputSize)
    fun readBiasH()     = readBuffer(bH, hiddenSize)
    fun readBiasO()     = readBuffer(bO, outputSize)

    fun writeWeightsIH(data: FloatArray) = writeBuffer(wIH, data)
    fun writeWeightsHO(data: FloatArray) = writeBuffer(wHO, data)
    fun writeBiasH(data: FloatArray)     = writeBuffer(bH, data)
    fun writeBiasO(data: FloatArray)     = writeBuffer(bO, data)

    // -----------------------------------------------------------------------
    // Private helpers — buffer creation
    // -----------------------------------------------------------------------

    private fun randomBuffer(size: Int): MTLBufferProtocol {
        val data = FloatArray(size) { (kotlin.random.Random.nextDouble() * 0.2 - 0.1).toFloat() }
        return sharedBuffer(data)
    }

    private fun zeroBuffer(size: Int): MTLBufferProtocol =
        device.newBufferWithLength((size * Float.SIZE_BYTES).toULong(), MTLResourceStorageModeShared)
            ?: error("Failed to allocate Metal buffer of size $size")

    /** Allocates a shared MTLBuffer and copies [data] into it. Zero-copy on Apple Silicon
     *  because CPU and GPU share the same physical memory. */
    private fun sharedBuffer(data: FloatArray): MTLBufferProtocol =
        data.usePinned { pinned ->
            device.newBufferWithBytes(
                bytes   = pinned.addressOf(0),
                length  = (data.size * Float.SIZE_BYTES).toULong(),
                options = MTLResourceStorageModeShared
            ) ?: error("Failed to allocate shared Metal buffer")
        }

    private fun readBuffer(buf: MTLBufferProtocol, size: Int): FloatArray {
        val ptr = buf.contents()?.reinterpret<FloatVar>()
            ?: error("MTLBuffer.contents() returned null")
        return FloatArray(size) { ptr[it] }
    }

    private fun writeBuffer(buf: MTLBufferProtocol, data: FloatArray) {
        val ptr = buf.contents()?.reinterpret<FloatVar>()
            ?: error("MTLBuffer.contents() returned null")
        data.forEachIndexed { i, v -> ptr[i] = v }
    }

    // -----------------------------------------------------------------------
    // Private helpers — kernel dispatch
    // -----------------------------------------------------------------------

    /** Dispatch a 2-D kernel covering [width × height] threads. */
    private fun MTLComputeCommandEncoderProtocol.dispatch2D(
        ps: MTLComputePipelineStateProtocol, width: Int, height: Int
    ) {
        val tg = 16uL
        dispatchThreads(
            threadsPerGrid        = MTLSizeMake(width.toULong(), height.toULong(), 1u),
            threadsPerThreadgroup = MTLSizeMake(tg, tg, 1u)
        )
    }

    /** Dispatch a 1-D kernel covering [count] threads. */
    private fun MTLComputeCommandEncoderProtocol.dispatch1D(
        ps: MTLComputePipelineStateProtocol, count: Int
    ) {
        val tg = ps.maxTotalThreadsPerThreadgroup.coerceAtMost(count.toULong())
        dispatchThreads(
            threadsPerGrid        = MTLSizeMake(count.toULong(), 1u, 1u),
            threadsPerThreadgroup = MTLSizeMake(tg, 1u, 1u)
        )
    }

    // C = A × B   →  [M × N]
    private fun encodeMatmul(
        enc: MTLComputeCommandEncoderProtocol,
        A: MTLBufferProtocol, B: MTLBufferProtocol, C: MTLBufferProtocol,
        M: Int, K: Int, N: Int
    ) = memScoped {
        enc.setComputePipelineState(psMatmul)
        enc.setBuffer(A, 0u, 0u); enc.setBuffer(B, 0u, 1u); enc.setBuffer(C, 0u, 2u)
        enc.setBytes(alloc<UIntVar>().apply { value = M.toUInt() }.ptr, 4u, 3u)
        enc.setBytes(alloc<UIntVar>().apply { value = K.toUInt() }.ptr, 4u, 4u)
        enc.setBytes(alloc<UIntVar>().apply { value = N.toUInt() }.ptr, 4u, 5u)
        enc.dispatch2D(psMatmul, N, M)
    }

    // C = Aᵀ × B   →  [M × N],  A is stored [K × M]
    private fun encodeMatmulAt(
        enc: MTLComputeCommandEncoderProtocol,
        A: MTLBufferProtocol, B: MTLBufferProtocol, C: MTLBufferProtocol,
        K: Int, M: Int, N: Int
    ) = memScoped {
        enc.setComputePipelineState(psMatmulAt)
        enc.setBuffer(A, 0u, 0u); enc.setBuffer(B, 0u, 1u); enc.setBuffer(C, 0u, 2u)
        enc.setBytes(alloc<UIntVar>().apply { value = K.toUInt() }.ptr, 4u, 3u)
        enc.setBytes(alloc<UIntVar>().apply { value = M.toUInt() }.ptr, 4u, 4u)
        enc.setBytes(alloc<UIntVar>().apply { value = N.toUInt() }.ptr, 4u, 5u)
        enc.dispatch2D(psMatmulAt, N, M)
    }

    // C = A × Bᵀ   →  [M × N],  B is stored [N × K]
    private fun encodeMatmulBt(
        enc: MTLComputeCommandEncoderProtocol,
        A: MTLBufferProtocol, B: MTLBufferProtocol, C: MTLBufferProtocol,
        M: Int, K: Int, N: Int
    ) = memScoped {
        enc.setComputePipelineState(psMatmulBt)
        enc.setBuffer(A, 0u, 0u); enc.setBuffer(B, 0u, 1u); enc.setBuffer(C, 0u, 2u)
        enc.setBytes(alloc<UIntVar>().apply { value = M.toUInt() }.ptr, 4u, 3u)
        enc.setBytes(alloc<UIntVar>().apply { value = K.toUInt() }.ptr, 4u, 4u)
        enc.setBytes(alloc<UIntVar>().apply { value = N.toUInt() }.ptr, 4u, 5u)
        enc.dispatch2D(psMatmulBt, N, M)
    }

    private fun encodeAddBias(
        enc: MTLComputeCommandEncoderProtocol,
        data: MTLBufferProtocol, bias: MTLBufferProtocol, rows: Int, cols: Int
    ) = memScoped {
        enc.setComputePipelineState(psAddBias)
        enc.setBuffer(data, 0u, 0u); enc.setBuffer(bias, 0u, 1u)
        enc.setBytes(alloc<UIntVar>().apply { value = cols.toUInt() }.ptr, 4u, 2u)
        enc.dispatch2D(psAddBias, cols, rows)
    }

    private fun encodeSigmoid(
        enc: MTLComputeCommandEncoderProtocol,
        data: MTLBufferProtocol, count: Int
    ) {
        enc.setComputePipelineState(psSigmoid)
        enc.setBuffer(data, 0u, 0u)
        enc.dispatch1D(psSigmoid, count)
    }

    private fun encodeSoftmax(
        enc: MTLComputeCommandEncoderProtocol,
        data: MTLBufferProtocol, rows: Int, cols: Int
    ) = memScoped {
        enc.setComputePipelineState(psSoftmax)
        enc.setBuffer(data, 0u, 0u)
        enc.setBytes(alloc<UIntVar>().apply { value = cols.toUInt() }.ptr, 4u, 1u)
        enc.dispatch1D(psSoftmax, rows)
    }

    private fun encodeOutputGradient(
        enc: MTLComputeCommandEncoderProtocol,
        output: MTLBufferProtocol, target: MTLBufferProtocol, cw: MTLBufferProtocol,
        grad: MTLBufferProtocol, batch: Int, outSz: Int
    ) = memScoped {
        enc.setComputePipelineState(psOutputGrad)
        enc.setBuffer(output, 0u, 0u); enc.setBuffer(target, 0u, 1u)
        enc.setBuffer(cw, 0u, 2u);     enc.setBuffer(grad, 0u, 3u)
        enc.setBytes(alloc<UIntVar>().apply { value = outSz.toUInt() }.ptr, 4u, 4u)
        enc.dispatch2D(psOutputGrad, outSz, batch)
    }

    private fun encodeHiddenGradient(
        enc: MTLComputeCommandEncoderProtocol,
        hiddenErr: MTLBufferProtocol, hidden: MTLBufferProtocol,
        hiddenGrad: MTLBufferProtocol, count: Int
    ) {
        enc.setComputePipelineState(psHiddenGrad)
        enc.setBuffer(hiddenErr, 0u, 0u); enc.setBuffer(hidden, 0u, 1u)
        enc.setBuffer(hiddenGrad, 0u, 2u)
        enc.dispatch1D(psHiddenGrad, count)
    }

    /** Scale [src] by [lr] in-place, then add to [dst]: dst += lr * src */
    private fun encodeScaleAndAdd(
        enc: MTLComputeCommandEncoderProtocol,
        dst: MTLBufferProtocol, src: MTLBufferProtocol,
        lr: Float, count: Int
    ) = memScoped {
        // scale src by lr
        enc.setComputePipelineState(psScaleInplace)
        enc.setBuffer(src, 0u, 0u)
        enc.setBytes(alloc<FloatVar>().apply { value = lr }.ptr, 4u, 1u)
        enc.dispatch1D(psScaleInplace, count)
        // dst += src
        enc.setComputePipelineState(psAddInplace)
        enc.setBuffer(dst, 0u, 0u); enc.setBuffer(src, 0u, 1u)
        enc.dispatch1D(psAddInplace, count)
    }

    /** Reduce [grad] columns into [tmp], scale by [lr], add to [bias]. */
    private fun encodeReduceAndAdd(
        enc: MTLComputeCommandEncoderProtocol,
        grad: MTLBufferProtocol, tmp: MTLBufferProtocol, bias: MTLBufferProtocol,
        rows: Int, cols: Int, lr: Float
    ) = memScoped {
        enc.setComputePipelineState(psReduceSumCols)
        enc.setBuffer(grad, 0u, 0u); enc.setBuffer(tmp, 0u, 1u)
        enc.setBytes(alloc<UIntVar>().apply { value = rows.toUInt() }.ptr, 4u, 2u)
        enc.setBytes(alloc<UIntVar>().apply { value = cols.toUInt() }.ptr, 4u, 3u)
        enc.dispatch1D(psReduceSumCols, cols)

        enc.setComputePipelineState(psScaleInplace)
        enc.setBuffer(tmp, 0u, 0u)
        enc.setBytes(alloc<FloatVar>().apply { value = lr }.ptr, 4u, 1u)
        enc.dispatch1D(psScaleInplace, cols)

        enc.setComputePipelineState(psAddInplace)
        enc.setBuffer(bias, 0u, 0u); enc.setBuffer(tmp, 0u, 1u)
        enc.dispatch1D(psAddInplace, cols)
    }
}
