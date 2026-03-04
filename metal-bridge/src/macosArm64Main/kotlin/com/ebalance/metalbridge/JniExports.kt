@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ebalance.metalbridge

import kotlinx.cinterop.*
import jni.*

// ---------------------------------------------------------------------------
// JNI bridge — exported as C symbols matching Java_<package>_<class>_<method>
// convention so that JVM's System.loadLibrary can find them without any extra
// registration step.
//
// The JVM counterpart is com.ebalance.classification.MetalNeuralNetwork.
//
// All array traffic crosses the JNI boundary via GetFloatArrayElements /
// ReleaseFloatArrayElements (pinned access — zero-copy if the JVM runtime
// decides not to copy, which HotSpot usually doesn't for short-lived pins).
// ---------------------------------------------------------------------------

// ── Stable-reference helpers ─────────────────────────────────────────────────

private fun ctxStore(ctx: MetalContext): jlong =
    StableRef.create(ctx).asCPointer().rawValue.toLong()

private fun ctxLoad(handle: jlong): MetalContext =
    interpretCPointer<CPointed>(handle)!!.asStableRef<MetalContext>().get()

private fun ctxFree(handle: jlong) =
    interpretCPointer<CPointed>(handle)!!.asStableRef<MetalContext>().dispose()

// ── JNI type-access helpers ───────────────────────────────────────────────────

private fun CPointer<JNIEnvVar>.table(): JNINativeInterface_ =
    pointed!!.pointed  // JNIEnv* → JNINativeInterface_* → JNINativeInterface_

private fun CPointer<JNIEnvVar>.len(arr: jarray?): Int =
    table().GetArrayLength!!.invoke(pointed, arr)

private fun CPointer<JNIEnvVar>.pinFloats(arr: jfloatArray?): CPointer<FloatVar>? =
    table().GetFloatArrayElements!!.invoke(pointed, arr, null)

private fun CPointer<JNIEnvVar>.unpinFloats(arr: jfloatArray?, ptr: CPointer<FloatVar>?) {
    table().ReleaseFloatArrayElements!!.invoke(pointed, arr, ptr, 0 /* JNI_COMMIT_AND_RELEASE */)
}

private fun CPointer<JNIEnvVar>.toKotlin(arr: jfloatArray?): FloatArray {
    val ptr = pinFloats(arr) ?: return FloatArray(0)
    val out = FloatArray(len(arr)) { ptr[it] }
    unpinFloats(arr, ptr)
    return out
}

private fun CPointer<JNIEnvVar>.newFloatArr(size: Int): jfloatArray? =
    table().NewFloatArray!!.invoke(pointed, size)

private fun CPointer<JNIEnvVar>.setFloatRegion(arr: jfloatArray?, data: FloatArray) {
    data.usePinned { pinned ->
        table().SetFloatArrayRegion!!.invoke(pointed, arr, 0, data.size, pinned.addressOf(0))
    }
}

// ── Exported JNI functions ────────────────────────────────────────────────────

/**
 * Creates a [MetalContext], returns an opaque Long handle.
 *
 * JVM declaration:
 *   `external fun createContext(inputSize: Int, hiddenSize: Int, outputSize: Int, maxBatch: Int): Long`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_createContext")
fun jniCreateContext(
    env: CPointer<JNIEnvVar>, clazz: jclass,
    inputSize: jint, hiddenSize: jint, outputSize: jint, maxBatch: jint
): jlong = ctxStore(MetalContext(inputSize, hiddenSize, outputSize, maxBatch))

/**
 * Runs one full-batch forward + backward pass and updates weights on the GPU.
 *
 * JVM declaration:
 *   `external fun trainBatch(ctx: Long, inputs: FloatArray, targets: FloatArray,
 *                             classWeights: FloatArray, batchSize: Int, lr: Float)`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_trainBatch")
fun jniTrainBatch(
    env: CPointer<JNIEnvVar>, clazz: jclass,
    ctx: jlong,
    inputsArr:  jfloatArray,
    targetsArr: jfloatArray,
    cwArr:      jfloatArray,
    batchSize:  jint,
    lr:         jfloat
) {
    val inputs  = env.toKotlin(inputsArr)
    val targets = env.toKotlin(targetsArr)
    val cw      = env.toKotlin(cwArr)
    ctxLoad(ctx).trainBatch(inputs, targets, cw, batchSize, lr)
}

/**
 * Single-sample prediction; returns a FloatArray of [outputSize] probabilities.
 *
 * JVM declaration:
 *   `external fun predict(ctx: Long, input: FloatArray): FloatArray`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_predict")
fun jniPredict(
    env: CPointer<JNIEnvVar>, clazz: jclass,
    ctx: jlong,
    inputArr: jfloatArray
): jfloatArray? {
    val input  = env.toKotlin(inputArr)
    val result = ctxLoad(ctx).predict(input)
    val arr    = env.newFloatArr(result.size) ?: return null
    env.setFloatRegion(arr, result)
    return arr
}

/**
 * Reads the input→hidden weight matrix back from GPU memory.
 * JVM declaration: `external fun getWeightsIH(ctx: Long): FloatArray`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_getWeightsIH")
fun jniGetWeightsIH(env: CPointer<JNIEnvVar>, clazz: jclass, ctx: jlong): jfloatArray? {
    val data = ctxLoad(ctx).readWeightsIH()
    val arr  = env.newFloatArr(data.size) ?: return null
    env.setFloatRegion(arr, data)
    return arr
}

/**
 * JVM declaration: `external fun getWeightsHO(ctx: Long): FloatArray`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_getWeightsHO")
fun jniGetWeightsHO(env: CPointer<JNIEnvVar>, clazz: jclass, ctx: jlong): jfloatArray? {
    val data = ctxLoad(ctx).readWeightsHO()
    val arr  = env.newFloatArr(data.size) ?: return null
    env.setFloatRegion(arr, data)
    return arr
}

/**
 * JVM declaration: `external fun getBiasH(ctx: Long): FloatArray`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_getBiasH")
fun jniGetBiasH(env: CPointer<JNIEnvVar>, clazz: jclass, ctx: jlong): jfloatArray? {
    val data = ctxLoad(ctx).readBiasH()
    val arr  = env.newFloatArr(data.size) ?: return null
    env.setFloatRegion(arr, data)
    return arr
}

/**
 * JVM declaration: `external fun getBiasO(ctx: Long): FloatArray`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_getBiasO")
fun jniGetBiasO(env: CPointer<JNIEnvVar>, clazz: jclass, ctx: jlong): jfloatArray? {
    val data = ctxLoad(ctx).readBiasO()
    val arr  = env.newFloatArr(data.size) ?: return null
    env.setFloatRegion(arr, data)
    return arr
}

/**
 * Frees the MetalContext and all associated GPU buffers.
 * JVM declaration: `external fun destroyContext(ctx: Long)`
 */
@CName("Java_com_ebalance_classification_MetalNeuralNetwork_destroyContext")
fun jniDestroyContext(env: CPointer<JNIEnvVar>, clazz: jclass, ctx: jlong) =
    ctxFree(ctx)
