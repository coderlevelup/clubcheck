package app.clubcheck.core

/** A plain pixel buffer (ARGB ints, row-major) so the codecs stay testable without Android. */
class RgbImage(val width: Int, val height: Int, val argb: IntArray) {
    init { require(argb.size == width * height) { "pixel count must equal width * height" } }

    fun copy() = RgbImage(width, height, argb.copyOf())

    /** Rec. 601 luma, 0..255. */
    fun luma(): FloatArray = FloatArray(argb.size) {
        val p = argb[it]
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }
}
