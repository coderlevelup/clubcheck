package app.clubcheck.core

import java.util.Random

/**
 * The visible data strip: a contained box along the bottom of the image, just under the repo line that labels
 * it. Coarse black and white cells (8 px) that survive JPEG and resizing, inside a black quiet zone (16 px at
 * each side, 8 px below). The first two and last two columns are alternating finder columns. A longer payload
 * (a longer club link) adds rows, not width.
 */
object StripCodec {
    const val CELL = 8
    const val MARGIN_X = 16
    const val MARGIN_BOTTOM = 8
    const val MAX_ROWS = 12
    private const val FINDER_COLS = 2
    private const val WHITE = -1          // 0xFFFFFFFF
    private const val BLACK = 0xFF000000.toInt()

    private fun totalCols(width: Int) = (width - 2 * MARGIN_X) / CELL
    private fun dataCols(width: Int) = totalCols(width) - 2 * FINDER_COLS

    fun capacityBytes(rows: Int, width: Int = EXPORT_WIDTH): Int = dataCols(width) * rows / 8
    fun rowsFor(codedBytes: Int, width: Int = EXPORT_WIDTH): Int = maxOf(1, Math.ceil(codedBytes * 8.0 / dataCols(width)).toInt())
    /** Height of the cell band alone. */
    fun heightPx(codedBytes: Int, width: Int = EXPORT_WIDTH): Int = rowsFor(codedBytes, width) * CELL
    /** Everything the strip takes from the bottom of the image: the band and its quiet zone below. */
    fun reservedPx(codedBytes: Int, width: Int = EXPORT_WIDTH): Int = heightPx(codedBytes, width) + MARGIN_BOTTOM

    fun draw(img: RgbImage, coded: ByteArray) {
        val rows = rowsFor(coded.size, img.width)
        require(rows <= MAX_ROWS) { "payload of ${coded.size} bytes needs $rows strip rows; at most $MAX_ROWS fit" }
        val cols = totalCols(img.width); val dCols = dataCols(img.width)
        val top = img.height - reservedPx(coded.size, img.width)
        for (y in top until img.height) for (x in 0 until img.width) img.argb[y * img.width + x] = BLACK     // quiet zone
        val rnd = Random(7)
        val bits = IntArray(rows * dCols) { i -> if (i < coded.size * 8) (coded[i / 8].toInt() shr (7 - i % 8)) and 1 else rnd.nextInt(2) }
        for (r in 0 until rows) for (c in 0 until cols) {
            val white = if (c < FINDER_COLS || c >= cols - FINDER_COLS) (r + c) % 2 == 1 else bits[r * dCols + (c - FINDER_COLS)] == 1
            val x0 = MARGIN_X + c * CELL; val y0 = top + r * CELL
            for (y in y0 until y0 + CELL) for (x in x0 until x0 + CELL) img.argb[y * img.width + x] = if (white) WHITE else BLACK
        }
    }

    fun read(img: RgbImage, codedLength: Int): ByteArray {
        val rows = rowsFor(codedLength, img.width)
        require(rows <= MAX_ROWS)
        val dCols = dataCols(img.width)
        val top = img.height - reservedPx(codedLength, img.width)
        val luma = img.luma()
        val out = ByteArray(codedLength)
        var n = 0
        loop@ for (r in 0 until rows) for (c in 0 until dCols) {
            if (n >= codedLength * 8) break@loop
            val x0 = MARGIN_X + (c + FINDER_COLS) * CELL; val y0 = top + r * CELL
            var sum = 0f; var count = 0
            for (y in y0 + 2 until y0 + CELL - 2) for (x in x0 + 2 until x0 + CELL - 2) { sum += luma[y * img.width + x]; count++ }
            if (sum / count > 127f) out[n / 8] = (out[n / 8].toInt() or (1 shl (7 - n % 8))).toByte()
            n++
        }
        return out
    }
}
