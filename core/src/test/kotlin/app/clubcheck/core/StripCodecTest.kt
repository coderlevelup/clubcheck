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

    @Test fun capacityIs139BytesFor720Rows() = assertEquals(139, StripCodec.capacityBytes(720))

    @Test fun payloadBeyondTheWidestStripThrows() {
        assertThrows(IllegalArgumentException::class.java) { StripCodec.draw(testPhoto(), ByteArray(StripCodec.capacityBytes(720, StripCodec.MAX_COLS) + 1)) }
    }

    @Test fun columnsGrowWithThePayload() {
        assertEquals(13, StripCodec.columnsFor(131, 720)); assertEquals(13, StripCodec.columnsFor(139, 720))
        assertEquals(14, StripCodec.columnsFor(140, 720)); assertEquals(19, StripCodec.columnsFor(194, 720))
        assertEquals(104, StripCodec.widthPx(131, 720)); assertEquals(152, StripCodec.widthPx(194, 720))
    }

    @Test fun aLongLinkWidensTheStripAndStillDecodesAfterJpeg() {
        val long = ReedSolomon.encode(ByteArray(154) { (it * 3 + 1).toByte() })     // 194 bytes: a 120-character link
        val img = testPhoto(); StripCodec.draw(img, long)
        assertArrayEquals(ByteArray(154) { (it * 3 + 1).toByte() }, ReedSolomon.decode(StripCodec.read(jpegRoundTrip(img, 60), long.size)))
    }

    @Test fun survivesJpegQ50() {
        val img = testPhoto(); StripCodec.draw(img, coded)
        val back = jpegRoundTrip(img, 50)
        assertArrayEquals(payload, ReedSolomon.decode(StripCodec.read(back, coded.size)))
    }

    @Test fun survivesResize60PlusJpegQ75() {
        val img = testPhoto(); StripCodec.draw(img, coded)
        val back = jpegRoundTrip(img, 75, scale = 0.6)
        assertArrayEquals(payload, ReedSolomon.decode(StripCodec.read(back, coded.size)))
    }

    @Test fun fillIsNotFlat() {
        val img = testPhoto(); StripCodec.draw(img, ByteArray(10))
        val bytes = StripCodec.read(img, StripCodec.capacityBytes(720))
        val unusedBytes = bytes.drop(10)
        assertTrue(unusedBytes.toSet().size > 8, "unused cells should look like data, not a flat block")
    }
}
