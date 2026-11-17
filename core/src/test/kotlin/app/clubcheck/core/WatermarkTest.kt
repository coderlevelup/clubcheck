package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.log10

class WatermarkTest {
    private val payload = ByteArray(91) { (it * 13 + 5).toByte() }
    private val coded = ReedSolomon.encode(payload)

    private fun marked(seed: Int = 1): RgbImage { val img = testPhoto(seed = seed); Watermark.embed(img, coded); return img }

    @Test fun roundTripsLossless() {
        assertArrayEquals(coded, Watermark.read(marked(), coded.size))
    }

    @Test fun survivesJpegQ50() {
        val back = jpegRoundTrip(marked(), 50)
        assertArrayEquals(payload, ReedSolomon.decode(Watermark.read(back, coded.size)))
    }

    @Test fun survivesResize60PlusJpegQ75() {
        val back = jpegRoundTrip(marked(), 75, scale = 0.6)
        assertArrayEquals(payload, ReedSolomon.decode(Watermark.read(back, coded.size)))
    }

    @Test fun readsAfterStripRemoved() {
        val img = marked()
        for (y in 720 - StripCodec.reservedPx(coded.size) until 720) for (x in 0 until 1280) img.argb[y * 1280 + x] = 0xFF000000.toInt()
        val back = jpegRoundTrip(img, 75)
        assertArrayEquals(payload, ReedSolomon.decode(Watermark.read(back, coded.size)))
    }

    @Test fun brightImageReportsWeak() {
        val white = RgbImage(1280, 720, IntArray(1280 * 720) { -1 })
        val report = Watermark.embed(white, coded)
        assertTrue(report.weak)
        assertEquals(0, report.blocksUsed)
    }

    @Test fun normalImageIsNotWeak() {
        val report = Watermark.embed(testPhoto(), coded)
        assertFalse(report.weak, "votes per bit was ${report.votesPerBit}")
    }

    @Test fun invisibleEnough() {
        val original = testPhoto(seed = 3)
        val img = original.copy(); Watermark.embed(img, coded)
        var se = 0.0
        for (i in img.argb.indices) for (shift in intArrayOf(16, 8, 0)) {
            val d = ((img.argb[i] shr shift) and 0xFF) - ((original.argb[i] shr shift) and 0xFF)
            se += d * d
        }
        val psnr = 10 * log10(255.0 * 255.0 / (se / (img.argb.size * 3)))
        assertTrue(psnr >= 38.0, "PSNR was $psnr dB")
    }

    @Test fun blackBarsDoNotCountAsUsableBlocks() {
        val black = RgbImage(1280, 720, IntArray(1280 * 720) { 0xFF000000.toInt() })
        val r = Watermark.embed(black, coded)
        assertEquals(0, r.blocksUsed); assertTrue(r.weak)
    }

    @Test fun neverClaimsAStrongWatermarkThatFailsAfterJpeg() {
        // a bright 4:3 photo letterboxed onto 1280x720 (what a default camera produces)
        val bright = fitToExport(testPhoto(1200, 900, seed = 9, brightness = 120))
        val report = Watermark.embed(bright, coded)
        val back = jpegRoundTrip(bright, 75)
        val decodes = ReedSolomon.decode(Watermark.read(back, coded.size)) != null
        assertTrue(report.weak || decodes, "not weak (${report.votesPerBit} votes/bit) yet it does not decode")
    }

    @Test fun aLongLinkPayloadSurvivesJpeg() {
        val long = ReedSolomon.encode(ByteArray(154) { (it * 5 + 2).toByte() })
        val img = testPhoto(); val report = Watermark.embed(img, long)
        assertFalse(report.weak, "votes per bit ${report.votesPerBit}")
        assertArrayEquals(ByteArray(154) { (it * 5 + 2).toByte() }, ReedSolomon.decode(Watermark.read(jpegRoundTrip(img, 75), long.size)))
    }
}
