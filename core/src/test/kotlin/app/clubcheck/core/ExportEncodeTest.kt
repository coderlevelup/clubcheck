package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExportEncodeTest {
    private val imageIoEncoder = JpegEncoder { img, q -> jpegBytes(img, q) }

    @Test fun exportBytesContainNoExifOrGps() {
        val bytes = encodeExportJpeg(testPhoto(), imageIoEncoder)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("Exif"), "no EXIF header")
        assertFalse(text.contains("GPS"), "no GPS tags")
        // no APP1 (0xFFE1, where EXIF and XMP live) segment before the image data starts
        var i = 2; var app1 = false
        while (i + 3 < bytes.size && (bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xFF) != 0xDA) {
            if ((bytes[i + 1].toInt() and 0xFF) == 0xE1) app1 = true
            i += 2 + (((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF))
        }
        assertFalse(app1, "no APP1 segment")
    }

    @Test fun usesQualityNinetyTwoByDefault() {
        var seen = -1
        encodeExportJpeg(testPhoto(), JpegEncoder { img, q -> seen = q; jpegBytes(img, q) })
        assertEquals(92, seen)
    }

    @Test fun refusesAnythingButTheExportSize() {
        assertThrows(IllegalArgumentException::class.java) { encodeExportJpeg(testPhoto(640, 360), imageIoEncoder) }
    }

    @Test fun encodedExportStillDecodesAfterJpegQuality92() {
        val p = Payload(byteArrayOf(1, 2, 3, 4, 5, 6), 1700000000L, 8, CoarseCell.NONE, Range(5, 6), Range(4, 7), 5, 1, byteArrayOf(1, 2, 3, 4), "club.example/x")
        val result = composeExport(testPhoto(), p, { _, _, _ -> }, "x")
        val bytes = encodeExportJpeg(result.image, imageIoEncoder)
        val back = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)).toRgb()
        val coded = ReedSolomon.encode(p.encode()).size
        assertEquals(p, Payload.decode(ReedSolomon.decode(StripCodec.read(back, coded))!!))
        assertEquals(p, Payload.decode(ReedSolomon.decode(Watermark.read(back, coded))!!))
    }
}
