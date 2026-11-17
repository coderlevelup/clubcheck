package app.clubcheck.core

import kotlin.math.abs

data class EmbedReport(val blocksUsed: Int, val blocksTotal: Int, val votesPerBit: Double) {
    /** Fewer than three voting blocks per bit: the hidden copy is unreliable (the strip still carries the data). */
    val weak: Boolean get() = votesPerBit < 3.0
}

/**
 * Hidden copy of the payload: quantisation index modulation on one low-frequency DCT coefficient of the
 * luma of dark 8x8 blocks, outside the strip. Each block votes for a bit chosen by a hash of its position,
 * so a few blocks changing darkness after JPEG does not shift the others.
 */
object Watermark {
    private const val STEP = 80.0
    private const val COEF = 1 * 8 + 1            // coefficient (1,1)
    private const val DARK_BELOW = 125.0
    /** Near-black blocks (letterbox bars, black frames) cannot carry the mark: QIM would clip at 0. */
    private const val TOO_BLACK = 24.0

    private fun bitIndex(bx: Int, by: Int, nbits: Int): Int =
        (((bx.toLong() * 73856093L) xor (by.toLong() * 19349663L)) % nbits).toInt()

    private fun block(luma: FloatArray, w: Int, bx: Int, by: Int) =
        DoubleArray(64) { luma[(by * 8 + it / 8) * w + bx * 8 + it % 8].toDouble() }

    fun embed(img: RgbImage, coded: ByteArray): EmbedReport {
        val nbits = coded.size * 8
        val luma = img.luma()
        val bw = img.width / 8; val bh = img.height / 8
        val firstBx = StripCodec.columnsFor(coded.size, img.height)     // one block column per strip column
        var used = 0
        for (by in 0 until bh) for (bx in firstBx until bw) {
            val blk = block(luma, img.width, bx, by)
            if (blk.average().let { it >= DARK_BELOW || it < TOO_BLACK }) continue
            val bit = (coded[bitIndex(bx, by, nbits) / 8].toInt() shr (7 - bitIndex(bx, by, nbits) % 8)) and 1
            val d = Dct.forward(blk)
            val off = bit * STEP / 2
            d[COEF] = Math.rint((d[COEF] - off) / STEP) * STEP + off
            val out = Dct.inverse(d)
            for (i in 0 until 64) {
                val x = bx * 8 + i % 8; val y = by * 8 + i / 8
                val delta = Math.rint(out[i]) - Math.rint(blk[i])
                val p = img.argb[y * img.width + x]
                fun ch(s: Int) = (((p shr s) and 0xFF) + delta.toInt()).coerceIn(0, 255)
                img.argb[y * img.width + x] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
            used++
        }
        return EmbedReport(used, (bw - firstBx) * bh, used.toDouble() / nbits)
    }

    fun read(img: RgbImage, codedLength: Int): ByteArray {
        val nbits = codedLength * 8
        val luma = img.luma()
        val bw = img.width / 8; val bh = img.height / 8
        val vote = DoubleArray(nbits)
        val firstBx = StripCodec.columnsFor(codedLength, img.height)
        for (by in 0 until bh) for (bx in firstBx until bw) {
            val blk = block(luma, img.width, bx, by)
            if (blk.average().let { it >= DARK_BELOW || it < TOO_BLACK }) continue
            val c = Dct.forward(blk)[COEF]
            val d0 = abs(c - Math.rint(c / STEP) * STEP)
            val d1 = abs(c - (Math.rint((c - STEP / 2) / STEP) * STEP + STEP / 2))
            vote[bitIndex(bx, by, nbits)] += (d0 - d1) / (STEP / 2)
        }
        val out = ByteArray(codedLength)
        for (i in 0 until nbits) if (vote[i] > 0) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
        return out
    }
}
