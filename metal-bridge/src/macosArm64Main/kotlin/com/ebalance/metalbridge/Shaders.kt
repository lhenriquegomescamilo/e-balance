package com.ebalance.metalbridge

/**
 * All Metal Shading Language (MSL) kernels, embedded as a compile-time string.
 * Compiled at runtime by [MetalContext] via `device.newLibraryWithSource(...)` —
 * no separate `.metal` file needed.
 */
internal val NN_SHADERS = """
#include <metal_stdlib>
using namespace metal;

// ---------------------------------------------------------------------------
// matmul:  C = A × B
//   A [M×K], B [K×N]  →  C [M×N]   (all row-major)
//   Each GPU thread computes one element of C.
// ---------------------------------------------------------------------------
kernel void matmul(
    device const float* A [[buffer(0)]],
    device const float* B [[buffer(1)]],
    device       float* C [[buffer(2)]],
    constant     uint&  M [[buffer(3)]],
    constant     uint&  K [[buffer(4)]],
    constant     uint&  N [[buffer(5)]],
    uint2 gid [[thread_position_in_grid]]
) {
    uint row = gid.y, col = gid.x;
    if (row >= M || col >= N) return;
    float acc = 0.0f;
    for (uint k = 0; k < K; k++) acc += A[row * K + k] * B[k * N + col];
    C[row * N + col] = acc;
}

// ---------------------------------------------------------------------------
// matmul_at:  C = Aᵀ × B
//   A [K×M] stored,  B [K×N]  →  C [M×N]
// ---------------------------------------------------------------------------
kernel void matmul_at(
    device const float* A [[buffer(0)]],
    device const float* B [[buffer(1)]],
    device       float* C [[buffer(2)]],
    constant     uint&  K [[buffer(3)]],
    constant     uint&  M [[buffer(4)]],
    constant     uint&  N [[buffer(5)]],
    uint2 gid [[thread_position_in_grid]]
) {
    uint row = gid.y, col = gid.x;
    if (row >= M || col >= N) return;
    float acc = 0.0f;
    for (uint k = 0; k < K; k++) acc += A[k * M + row] * B[k * N + col];
    C[row * N + col] = acc;
}

// ---------------------------------------------------------------------------
// matmul_bt:  C = A × Bᵀ
//   A [M×K],  B [N×K] stored  →  C [M×N]
// ---------------------------------------------------------------------------
kernel void matmul_bt(
    device const float* A [[buffer(0)]],
    device const float* B [[buffer(1)]],
    device       float* C [[buffer(2)]],
    constant     uint&  M [[buffer(3)]],
    constant     uint&  K [[buffer(4)]],
    constant     uint&  N [[buffer(5)]],
    uint2 gid [[thread_position_in_grid]]
) {
    uint row = gid.y, col = gid.x;
    if (row >= M || col >= N) return;
    float acc = 0.0f;
    for (uint k = 0; k < K; k++) acc += A[row * K + k] * B[col * K + k];
    C[row * N + col] = acc;
}

// ---------------------------------------------------------------------------
// add_bias:  data[i][j] += bias[j]   (broadcast a bias vector over every row)
// ---------------------------------------------------------------------------
kernel void add_bias(
    device       float* data [[buffer(0)]],
    device const float* bias [[buffer(1)]],
    constant     uint& cols  [[buffer(2)]],
    uint2 gid [[thread_position_in_grid]]
) {
    data[gid.y * cols + gid.x] += bias[gid.x];
}

// ---------------------------------------------------------------------------
// sigmoid_inplace:  data[i] = 1 / (1 + exp(-data[i]))
// ---------------------------------------------------------------------------
kernel void sigmoid_inplace(
    device float* data [[buffer(0)]],
    uint id [[thread_position_in_grid]]
) {
    data[id] = 1.0f / (1.0f + exp(-data[id]));
}

// ---------------------------------------------------------------------------
// softmax_rows:  per-row numerically-stable softmax
//   One thread per row; iterates over cols serially (cols is small, ~47).
// ---------------------------------------------------------------------------
kernel void softmax_rows(
    device       float* data  [[buffer(0)]],
    constant     uint&  cols  [[buffer(1)]],
    uint row [[thread_position_in_grid]]
) {
    device float* r = data + row * cols;
    float mx = r[0];
    for (uint j = 1; j < cols; j++) mx = max(mx, r[j]);
    float s = 0.0f;
    for (uint j = 0; j < cols; j++) { r[j] = exp(r[j] - mx); s += r[j]; }
    for (uint j = 0; j < cols; j++) r[j] /= s;
}

// ---------------------------------------------------------------------------
// output_gradient:
//   grad[i][j] = (target[i][j] - output[i][j]) * classWeight[i]
// ---------------------------------------------------------------------------
kernel void output_gradient(
    device const float* output      [[buffer(0)]],
    device const float* target      [[buffer(1)]],
    device const float* classWeight [[buffer(2)]],
    device       float* grad        [[buffer(3)]],
    constant     uint&  outputSize  [[buffer(4)]],
    uint2 gid [[thread_position_in_grid]]   // gid.y = sample, gid.x = class
) {
    uint idx = gid.y * outputSize + gid.x;
    grad[idx] = (target[idx] - output[idx]) * classWeight[gid.y];
}

// ---------------------------------------------------------------------------
// hidden_gradient:
//   hidden_grad[i] = hidden_error[i] * h[i] * (1 - h[i])
//   (sigmoid derivative; h is already post-sigmoid)
// ---------------------------------------------------------------------------
kernel void hidden_gradient(
    device const float* hidden_error [[buffer(0)]],
    device const float* hidden       [[buffer(1)]],
    device       float* hidden_grad  [[buffer(2)]],
    uint id [[thread_position_in_grid]]
) {
    float h = hidden[id];
    hidden_grad[id] = hidden_error[id] * h * (1.0f - h);
}

// ---------------------------------------------------------------------------
// add_inplace:  dst[i] += src[i]
//   Used to apply weight gradient update: weights += dWeights
// ---------------------------------------------------------------------------
kernel void add_inplace(
    device       float* dst [[buffer(0)]],
    device const float* src [[buffer(1)]],
    uint id [[thread_position_in_grid]]
) {
    dst[id] += src[id];
}

// ---------------------------------------------------------------------------
// scale_inplace:  data[i] *= scale
//   Used to multiply gradient buffers by the learning rate before adding.
// ---------------------------------------------------------------------------
kernel void scale_inplace(
    device   float* data  [[buffer(0)]],
    constant float& scale [[buffer(1)]],
    uint id [[thread_position_in_grid]]
) {
    data[id] *= scale;
}

// ---------------------------------------------------------------------------
// reduce_sum_cols:  out[j] = Σᵢ data[i][j]
//   One thread per column; sums over rows (bias gradient accumulation).
// ---------------------------------------------------------------------------
kernel void reduce_sum_cols(
    device const float* data [[buffer(0)]],
    device       float* out  [[buffer(1)]],
    constant     uint&  rows [[buffer(2)]],
    constant     uint&  cols [[buffer(3)]],
    uint col [[thread_position_in_grid]]
) {
    if (col >= cols) return;
    float s = 0.0f;
    for (uint i = 0; i < rows; i++) s += data[i * cols + col];
    out[col] = s;
}
"""
