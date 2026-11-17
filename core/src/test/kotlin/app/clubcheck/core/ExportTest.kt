package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ExportTest {
    private fun payload(
        hand: Range = Range(55, 65), camera: Range = Range(56, 74), cell: CoarseCell = CoarseCell(-3390, 1840),
        ts: Long = 1700000000L, tz: Int = 8,
    ) = Payload(
        eventId = byteArrayOf(1, 2, 3, 4, 5, 6), utcSeconds = ts, tz15 = tz, cell = cell, hand = hand, camera = camera,
        cameraPoint = 60, photos = 1, installId = byteArrayOf(9, 8, 7, 6), url = "rsvp.withgoogle.com/events/android-developer-day-capetown",
    )

    @Test fun overlayLinesFormatsRangesAndFlag() {
        val ok = overlayLines(payload(), "Android Developer Day", ZoneOffset.ofHours(2))
        assertEquals("ANDROID DEVELOPER DAY", ok[0])
        assertEquals("HAND 55-65  CAMERA 56-74  RANGES OVERLAP", ok[3])
        val bad = overlayLines(payload(hand = Range(10, 12), camera = Range(40, 50)), "x", ZoneOffset.ofHours(2))
        assertEquals("HAND 10-12  CAMERA 40-50  CHECK COUNTS", bad[3])
    }

    @Test fun overlayShowsLocalTimeAndArea() {
        assertEquals("15 NOV 2023 00:13 UTC+2   33.90S 18.40E", overlayLines(payload(), "x", ZoneOffset.ofHours(2))[2])
        assertTrue(overlayLines(payload(tz = 22), "x", ZoneOffset.ofHoursMinutes(5, 30))[2].startsWith("15 NOV 2023 03:43 UTC+5:30"))
    }

    @Test fun overlayOmitsAreaWithoutLocation() {
        assertEquals("15 NOV 2023 00:13 UTC+2", overlayLines(payload(cell = CoarseCell.NONE), "x", ZoneOffset.ofHours(2))[2])
    }

    @Test fun overlayShowsSingleNumberWhenRangeIsOnePoint() {
        assertEquals("HAND 60  CAMERA 56-74  RANGES OVERLAP", overlayLines(payload(hand = Range(60, 60)), "x", ZoneOffset.ofHours(2))[3])
    }

    @Test fun composeThenDecodeBothLayers() {
        val p = payload()
        val result = composeExport(testPhoto(), p, { _, _, _ -> }, "Android Developer Day")
        val back = jpegRoundTrip(result.image, 75)
        val coded = ReedSolomon.encode(p.encode()).size
        assertEquals(p, Payload.decode(ReedSolomon.decode(StripCodec.read(back, coded))!!))
        assertEquals(p, Payload.decode(ReedSolomon.decode(Watermark.read(back, coded))!!))
        assertFalse(result.report.weak)
    }

    @Test fun overlayCallbackReceivesTheLines() {
        var seen: List<String>? = null
        composeExport(testPhoto(), payload(), { _, lines, _ -> seen = lines }, "Club")
        assertEquals(4, seen!!.size)
        assertEquals("CLUB", seen!![0])
        assertEquals("rsvp.withgoogle.com/events/android-developer-day-capetown", seen!![1])   // the link, readable in the header
    }

    @Test fun exportIsExactly1280x720AndInputUntouched() {
        val input = testPhoto(); val snapshot = input.argb.copyOf()
        val result = composeExport(input, payload(), { _, _, _ -> }, "x")
        assertEquals(1280, result.image.width); assertEquals(720, result.image.height)
        assertArrayEquals(snapshot, input.argb, "the input image must not be modified")
    }

    @Test fun noSeparateLinkLineWhenTheTitleIsTheLink() {
        val p = payload()
        assertEquals(3, overlayLines(p, p.url, ZoneOffset.ofHours(2)).size)
        assertEquals(4, overlayLines(p, "A Club", ZoneOffset.ofHours(2)).size)
    }

    @Test fun anyPhotoSizeIsFittedIntoThe1280x720Export() {
        val r = composeExport(testPhoto(4000, 2252, seed = 4), payload(), { _, _, _ -> }, "x")
        assertEquals(1280, r.image.width); assertEquals(720, r.image.height)
    }

    @Test fun theStripSitsBesideThePhotoNotOnIt() {
        val white = RgbImage(1280, 720, IntArray(1280 * 720) { -1 })           // a bright photo: nothing dark to watermark
        val img = composeExport(white, payload(), { _, _, _ -> }, "x").image
        val strip = StripCodec.widthPx(ReedSolomon.encode(payload().encode()).size, 720)
        assertEquals(104, strip)
        for (x in 0 until strip) assertTrue((img.argb[360 * 1280 + x] and 0xFFFFFF).let { it == 0 || it == 0xFFFFFF }, "strip column $x is pure black or white")
        assertTrue((img.argb[360 * 1280 + strip + 100] and 0xFF) > 225, "photo starts right after the strip")
        assertTrue((img.argb[5 * 1280 + strip + 100] and 0xFF) < 30, "the photo is fitted (bars above), not stretched under the strip")
    }

    @Test fun overlayCallbackLearnsTheStripWidthSoTextStaysClearOfIt() {
        var narrow = -1; var wide = -1
        composeExport(testPhoto(), payload(), { _, _, w -> narrow = w }, "x")
        composeExport(testPhoto(), payload().copy(url = "club.example/" + "a".repeat(107)), { _, _, w -> wide = w }, "x")
        assertEquals(104, narrow); assertEquals(152, wide)
    }

    @Test fun aLongLinkExportDecodesBothLayers() {
        val p = payload().copy(url = "club.example/" + "a".repeat(107))
        val back = jpegRoundTrip(composeExport(testPhoto(), p, { _, _, _ -> }, "x").image, 75)
        assertEquals(p, decodeExport(back)!!.strip); assertEquals(p, decodeExport(back)!!.watermark)
    }
}
