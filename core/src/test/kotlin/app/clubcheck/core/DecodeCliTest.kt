package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

class DecodeCliTest {
    private val whatsapp = File("../spikes/2026-11-17-event-photo/images/received-via-whatsapp.jpg")
    private val export = File("../spikes/2026-11-17-event-photo/images/export-1280x720.png")

    private fun load(f: File) = ImageIO.read(f).toRgb()

    @Test fun decodesSpikeWhatsAppImage() {
        val d = decodeExport(load(whatsapp))!!
        assertEquals("rsvp.withgoogle.com/events/android-developer-day-capetown", d.payload.url)
        assertEquals(Range(56, 74), d.payload.camera)
        assertEquals(Range(55, 65), d.payload.hand)
        assertEquals(60, d.payload.cameraPoint)
        assertEquals(CoarseCell(-3390, 1840), d.payload.cell)
        assertEquals(8, d.payload.tz15)
    }

    @Test fun bothLayersAgreeOnTheRealWhatsAppImage() {
        val d = decodeExport(load(whatsapp))!!
        assertTrue(d.viaStrip, "strip decoded"); assertTrue(d.viaWatermark, "watermark decoded"); assertTrue(d.layersAgree)
    }

    @Test fun decodesTheLosslessSpikeExport() {
        val d = decodeExport(load(export))!!
        assertEquals("rsvp.withgoogle.com/events/android-developer-day-capetown", d.payload.url)
    }

    @Test fun watermarkAloneIsEnoughWhenTheStripIsCoveredUp() {
        val img = load(whatsapp)
        for (y in 0 until img.height) for (x in 0 until StripCodec.WIDTH_PX) img.argb[y * img.width + x] = 0xFF000000.toInt()
        val d = decodeExport(img)!!
        assertFalse(d.viaStrip); assertTrue(d.viaWatermark)
        assertEquals(Range(56, 74), d.payload.camera)
    }

    @Test fun decodesOurOwnExportsOfAnyUrlLength() {
        for (url in listOf("a.b", "club.example/chess", "x".repeat(120))) {
            val p = Payload(byteArrayOf(1, 2, 3, 4, 5, 6), 1700000000L, 8, CoarseCell.NONE, Range(1, 2), Range(1, 3), 2, 1, byteArrayOf(1, 2, 3, 4), url)
            val back = jpegRoundTrip(composeExport(testPhoto(), p, { _, _, _ -> }, "t").image, 75)
            assertEquals(p, decodeExport(back)!!.payload, "url length ${url.length}")
        }
    }

    @Test fun anOrdinaryPhotoDecodesToNothing() = assertNull(decodeExport(testPhoto()))

    @Test fun cliPrintsJsonForAnImageFile() {
        val out = java.io.ByteArrayOutputStream()
        runDecodeCli(arrayOf(whatsapp.path), java.io.PrintStream(out))
        val text = out.toString()
        assertTrue(text.contains("\"url\": \"rsvp.withgoogle.com/events/android-developer-day-capetown\""), text)
        assertTrue(text.contains("\"camera\": [56, 74]"), text)
    }
}
