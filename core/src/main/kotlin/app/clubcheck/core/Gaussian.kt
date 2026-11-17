package app.clubcheck.core

import kotlin.math.ceil
import kotlin.math.exp

/** Separable Gaussian blur of a [w] x [h] single-channel plane, edges clamped. */
fun gaussianBlurPlane(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
    val radius = ceil(3 * sigma).toInt().coerceAtLeast(1)
    val kernel = FloatArray(2 * radius + 1) { exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
    val sum = kernel.sum(); for (i in kernel.indices) kernel[i] /= sum
    val tmp = FloatArray(src.size); val out = FloatArray(src.size)
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -radius..radius) s += src[y * w + (x + k).coerceIn(0, w - 1)] * kernel[k + radius]
        tmp[y * w + x] = s
    }
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -radius..radius) s += tmp[(y + k).coerceIn(0, h - 1) * w + x] * kernel[k + radius]
        out[y * w + x] = s
    }
    return out
}
