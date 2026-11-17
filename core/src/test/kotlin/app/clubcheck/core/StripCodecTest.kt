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

    @Test fun payloadTooLargeThrows() {
        assertThrows(IllegalArgumentException::class.java) { StripCodec.draw(testPhoto(), ByteArray(140)) }
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
