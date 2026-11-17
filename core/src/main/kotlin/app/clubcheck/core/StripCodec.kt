package app.clubcheck.core

import java.util.Random

/**
 * The visible data strip down the left edge, like a film soundtrack: coarse black and white cells
 * that survive JPEG and resizing. Rows 0-1 and the last two rows are alternating finder rows.
 */
object StripCodec {
    const val CELL = 8
    /** Narrowest strip (13 columns, 104 px): enough for a payload of up to 139 bytes. A longer link widens it. */
    const val COLS = 13
    const val MAX_COLS = 24
    const val WIDTH_PX = CELL * COLS
    private const val FINDER_ROWS = 2
    private const val WHITE = -1          // 0xFFFFFFFF
    private const val BLACK = 0xFF000000.toInt()

    private fun dataRows(height: Int) = height / CELL - 2 * FINDER_ROWS

    fun capacityBytes(height: Int, cols: Int = COLS): Int = dataRows(height) * cols / 8

    /** Columns needed for [codedBytes]: the default width, or wider when the payload (a long link) needs it. */
    fun columnsFor(codedBytes: Int, height: Int): Int = maxOf(COLS, Math.ceil(codedBytes * 8.0 / dataRows(height)).toInt())

    fun widthPx(codedBytes: Int, height: Int): Int = columnsFor(codedBytes, height) * CELL

    fun draw(img: RgbImage, coded: ByteArray) {
        val cols = columnsFor(coded.size, img.height)
        require(cols <= MAX_COLS) { "payload of ${coded.size} bytes needs $cols strip columns; at most $MAX_COLS fit" }
        val rows = img.height / CELL
        val cells = dataRows(img.height) * cols
        val rnd = Random(7)
        val bits = IntArray(cells) { i ->
            if (i < coded.size * 8) (coded[i / 8].toInt() shr (7 - i % 8)) and 1 else rnd.nextInt(2)
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val white = if (r < FINDER_ROWS || r >= rows - FINDER_ROWS) (r + c) % 2 == 1
            else bits[(r - FINDER_ROWS) * cols + c] == 1
            val colour = if (white) WHITE else BLACK
            for (y in r * CELL until (r + 1) * CELL) for (x in c * CELL until (c + 1) * CELL) img.argb[y * img.width + x] = colour
        }
    }

    fun read(img: RgbImage, codedLength: Int): ByteArray {
        val cols = columnsFor(codedLength, img.height)
        require(cols <= MAX_COLS)
        val luma = img.luma()
        val rows = img.height / CELL
        val out = ByteArray(codedLength)
        var n = 0
        loop@ for (r in FINDER_ROWS until rows - FINDER_ROWS) for (c in 0 until cols) {
            if (n >= codedLength * 8) break@loop
            var sum = 0f; var count = 0
            for (y in r * CELL + 2 until (r + 1) * CELL - 2) for (x in c * CELL + 2 until (c + 1) * CELL - 2) { sum += luma[y * img.width + x]; count++ }
            if (sum / count > 127f) out[n / 8] = (out[n / 8].toInt() or (1 shl (7 - n % 8))).toByte()
            n++
        }
        return out
    }
}
