package app.clubcheck.core

import java.util.Random

/**
 * The visible data strip down the left edge, like a film soundtrack: coarse black and white cells
 * that survive JPEG and resizing. Rows 0-1 and the last two rows are alternating finder rows.
 */
object StripCodec {
    const val CELL = 8
    const val COLS = 13
    const val WIDTH_PX = CELL * COLS
    private const val FINDER_ROWS = 2
    private const val WHITE = -1          // 0xFFFFFFFF
    private const val BLACK = 0xFF000000.toInt()

    private fun dataRows(height: Int) = height / CELL - 2 * FINDER_ROWS

    fun capacityBytes(height: Int): Int = dataRows(height) * COLS / 8

    fun draw(img: RgbImage, coded: ByteArray) {
        require(coded.size <= capacityBytes(img.height)) { "payload of ${coded.size} bytes exceeds strip capacity ${capacityBytes(img.height)}" }
        val rows = img.height / CELL
        val cells = dataRows(img.height) * COLS
        val rnd = Random(7)
        val bits = IntArray(cells) { i ->
            if (i < coded.size * 8) (coded[i / 8].toInt() shr (7 - i % 8)) and 1 else rnd.nextInt(2)
        }
        for (r in 0 until rows) for (c in 0 until COLS) {
            val white = if (r < FINDER_ROWS || r >= rows - FINDER_ROWS) (r + c) % 2 == 1
            else bits[(r - FINDER_ROWS) * COLS + c] == 1
            val colour = if (white) WHITE else BLACK
            for (y in r * CELL until (r + 1) * CELL) for (x in c * CELL until (c + 1) * CELL) img.argb[y * img.width + x] = colour
        }
    }

    fun read(img: RgbImage, codedLength: Int): ByteArray {
        require(codedLength <= capacityBytes(img.height))
        val luma = img.luma()
        val rows = img.height / CELL
        val out = ByteArray(codedLength)
        var n = 0
        loop@ for (r in FINDER_ROWS until rows - FINDER_ROWS) for (c in 0 until COLS) {
            if (n >= codedLength * 8) break@loop
            var sum = 0f; var count = 0
            for (y in r * CELL + 2 until (r + 1) * CELL - 2) for (x in c * CELL + 2 until (c + 1) * CELL - 2) { sum += luma[y * img.width + x]; count++ }
            if (sum / count > 127f) out[n / 8] = (out[n / 8].toInt() or (1 shl (7 - n % 8))).toByte()
            n++
        }
        return out
    }
}
