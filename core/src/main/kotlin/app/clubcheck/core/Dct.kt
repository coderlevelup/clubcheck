package app.clubcheck.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Orthonormal 8x8 DCT-II / inverse (the same scaling OpenCV's `dct` uses). */
object Dct {
    private val basis = Array(8) { k ->
        DoubleArray(8) { n -> (if (k == 0) sqrt(1.0 / 8) else sqrt(2.0 / 8)) * cos(PI * (2 * n + 1) * k / 16.0) }
    }

    /** [block] is 64 values row-major. Returns the 64 coefficients row-major. */
    fun forward(block: DoubleArray): DoubleArray {
        val tmp = DoubleArray(64); val out = DoubleArray(64)
        for (y in 0 until 8) for (u in 0 until 8) { var s = 0.0; for (x in 0 until 8) s += block[y * 8 + x] * basis[u][x]; tmp[y * 8 + u] = s }
        for (u in 0 until 8) for (v in 0 until 8) { var s = 0.0; for (y in 0 until 8) s += tmp[y * 8 + u] * basis[v][y]; out[v * 8 + u] = s }
        return out
    }

    fun inverse(coef: DoubleArray): DoubleArray {
        val tmp = DoubleArray(64); val out = DoubleArray(64)
        for (v in 0 until 8) for (x in 0 until 8) { var s = 0.0; for (u in 0 until 8) s += coef[v * 8 + u] * basis[u][x]; tmp[v * 8 + x] = s }
        for (x in 0 until 8) for (y in 0 until 8) { var s = 0.0; for (v in 0 until 8) s += tmp[v * 8 + x] * basis[v][y]; out[y * 8 + x] = s }
        return out
    }
}
