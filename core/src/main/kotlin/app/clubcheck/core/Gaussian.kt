package app.clubcheck.core

import kotlin.math.ceil
import kotlin.math.exp

/**
 * Gaussian blur of a [w] x [h] plane. Large sigmas (close-up heads) are blurred at reduced size and scaled
 * back up, which costs a small fraction of the exact convolution and is visually the same for a Gaussian.
 */
fun gaussianBlurPlane(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
    if (sigma <= 6f) return gaussianBlurPlaneExact(src, w, h, sigma)
    val k = (sigma / 3f).toInt().coerceAtLeast(2)
    val sw = (w + k - 1) / k; val sh = (h + k - 1) / k
    val small = FloatArray(sw * sh)
    for (sy in 0 until sh) for (sx in 0 until sw) {
        var sum = 0f; var n = 0
        for (y in sy * k until minOf(h, sy * k + k)) for (x in sx * k until minOf(w, sx * k + k)) { sum += src[y * w + x]; n++ }
        small[sy * sw + sx] = sum / n
    }
    val blurred = gaussianBlurPlaneExact(small, sw, sh, sigma / k)
    val out = FloatArray(w * h)
    for (y in 0 until h) {
        val fy = ((y + 0.5f) / k - 0.5f).coerceIn(0f, sh - 1f); val y0 = fy.toInt(); val y1 = minOf(y0 + 1, sh - 1); val ty = fy - y0
        for (x in 0 until w) {
            val fx = ((x + 0.5f) / k - 0.5f).coerceIn(0f, sw - 1f); val x0 = fx.toInt(); val x1 = minOf(x0 + 1, sw - 1); val tx = fx - x0
            val top = blurred[y0 * sw + x0] * (1 - tx) + blurred[y0 * sw + x1] * tx
            val bottom = blurred[y1 * sw + x0] * (1 - tx) + blurred[y1 * sw + x1] * tx
            out[y * w + x] = top * (1 - ty) + bottom * ty
        }
    }
    return out
}

/** Exact separable Gaussian blur, edges clamped. */
fun gaussianBlurPlaneExact(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
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
