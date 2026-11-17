package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StripCodecTest {
    private val payload = ByteArray(91) { (it * 7 + 3).toByte() }
    private val coded = ReedSolomon.encode(payload)

    @Test fun drawThenReadIsExact() {
        val img = testPhoto()
        StripCodec.draw(img, coded)
        assertArrayEquals(coded, StripCodec.read(img, coded.size))
    }

    @Test fun theStripIsAContainedBoxAtTheBottom() {
        val img = RgbImage(1280, 720, IntArray(1280 * 720) { 0xFF336699.toInt() })
        StripCodec.draw(img, coded)
        val top = 720 - StripCodec.reservedPx(coded.size) + 0          // reserved = band + bottom margin
        assertEquals(64, StripCodec.reservedPx(coded.size)); assertEquals(56, StripCodec.heightPx(coded.size))
        assertEquals(0xFF336699.toInt(), img.argb[(top - 1) * 1280 + 640], "nothing above the box is touched")
        assertEquals(0xFF000000.toInt(), img.argb[(top + 3) * 1280 + 5], "left margin is a black quiet zone")
        assertEquals(0xFF000000.toInt(), img.argb[(720 - 3) * 1280 + 640], "bottom margin is a black quiet zone")
        for (x in 16 until 1264) assertTrue((img.argb[(top + 3) * 1280 + x] and 0xFFFFFF).let { it == 0 || it == 0xFFFFFF }, "band pixel $x is pure black or white")
    }

    @Test fun rowsGrowWithThePayload() {
        assertEquals(7, StripCodec.rowsFor(131)); assertEquals(7, StripCodec.rowsFor(133))
        assertEquals(8, StripCodec.rowsFor(134)); assertEquals(11, StripCodec.rowsFor(194))
        assertEquals(56, StripCodec.heightPx(131)); assertEquals(88, StripCodec.heightPx(194)); assertEquals(96, StripCodec.reservedPx(194))
        assertEquals(133, StripCodec.capacityBytes(7))
    }

    @Test fun payloadBeyondTheTallestStripThrows() {
        assertThrows(IllegalArgumentException::class.java) { StripCodec.draw(testPhoto(), ByteArray(StripCodec.capacityBytes(StripCodec.MAX_ROWS) + 1)) }
    }

    @Test fun finderColumnsAlternateAtBothEnds() {
        val img = testPhoto(); StripCodec.draw(img, coded)
        val top = 720 - StripCodec.reservedPx(coded.size)
        for (r in 0 until StripCodec.rowsFor(coded.size)) for (c in listOf(0, 1, 154, 155)) {
            val white = (img.argb[(top + r * 8 + 3) * 1280 + 16 + c * 8 + 3] and 0xFF) > 127
            assertEquals((r + c) % 2 == 1, white, "finder cell row $r col $c")
        }
    }

    @Test fun survivesJpegQ50() {
        val img = testPhoto(); StripCodec.draw(img, coded)
        assertArrayEquals(payload, ReedSolomon.decode(StripCodec.read(jpegRoundTrip(img, 50), coded.size)))
    }

    @Test fun survivesResize60PlusJpegQ75() {
        val img = testPhoto(); StripCodec.draw(img, coded)
        assertArrayEquals(payload, ReedSolomon.decode(StripCodec.read(jpegRoundTrip(img, 75, scale = 0.6), coded.size)))
    }

    @Test fun aLongLinkAddsRowsAndStillDecodesAfterJpeg() {
        val data = ByteArray(154) { (it * 3 + 1).toByte() }
        val long = ReedSolomon.encode(data)                      // 194 bytes: a 120-character link
        val img = testPhoto(); StripCodec.draw(img, long)
        assertArrayEquals(data, ReedSolomon.decode(StripCodec.read(jpegRoundTrip(img, 60), long.size)))
    }

    @Test fun fillIsNotFlat() {
        val img = testPhoto(); StripCodec.draw(img, ByteArray(40))
        val unused = StripCodec.read(img, StripCodec.capacityBytes(StripCodec.rowsFor(40))).drop(40)
        assertTrue(unused.toSet().size > 8, "unused cells should look like data, not a flat block")
    }
}
