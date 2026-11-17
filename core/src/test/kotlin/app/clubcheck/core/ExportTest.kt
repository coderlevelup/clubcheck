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
        assertEquals("HAND 55-65  CAMERA 56-74  RANGES OVERLAP", ok[2])
        val bad = overlayLines(payload(hand = Range(10, 12), camera = Range(40, 50)), "x", ZoneOffset.ofHours(2))
        assertEquals("HAND 10-12  CAMERA 40-50  CHECK COUNTS", bad[2])
    }

    @Test fun overlayShowsLocalTimeAndArea() {
        assertEquals("15 NOV 2023 00:13 UTC+2   33.90S 18.40E", overlayLines(payload(), "x", ZoneOffset.ofHours(2))[1])
        assertTrue(overlayLines(payload(tz = 22), "x", ZoneOffset.ofHoursMinutes(5, 30))[1].startsWith("15 NOV 2023 03:43 UTC+5:30"))
    }

    @Test fun overlayOmitsAreaWithoutLocation() {
        assertEquals("15 NOV 2023 00:13 UTC+2", overlayLines(payload(cell = CoarseCell.NONE), "x", ZoneOffset.ofHours(2))[1])
    }

    @Test fun overlayShowsSingleNumberWhenRangeIsOnePoint() {
        assertEquals("HAND 60  CAMERA 56-74  RANGES OVERLAP", overlayLines(payload(hand = Range(60, 60)), "x", ZoneOffset.ofHours(2))[2])
    }

    @Test fun composeThenDecodeBothLayers() {
        val p = payload()
        val result = composeExport(testPhoto(), p, { _, _ -> }, "Android Developer Day")
        val back = jpegRoundTrip(result.image, 75)
        val coded = ReedSolomon.encode(p.encode()).size
        assertEquals(p, Payload.decode(ReedSolomon.decode(StripCodec.read(back, coded))!!))
        assertEquals(p, Payload.decode(ReedSolomon.decode(Watermark.read(back, coded))!!))
        assertFalse(result.report.weak)
    }

    @Test fun overlayCallbackReceivesTheLines() {
        var seen: List<String>? = null
        composeExport(testPhoto(), payload(), { _, lines -> seen = lines }, "Club")
        assertEquals(3, seen!!.size)
        assertEquals("CLUB", seen!![0])
    }

    @Test fun exportIsExactly1280x720AndInputUntouched() {
        val input = testPhoto(); val snapshot = input.argb.copyOf()
        val result = composeExport(input, payload(), { _, _ -> }, "x")
        assertEquals(1280, result.image.width); assertEquals(720, result.image.height)
        assertArrayEquals(snapshot, input.argb, "the input image must not be modified")
    }

    @Test fun rejectsWrongSize() {
        assertThrows(IllegalArgumentException::class.java) { composeExport(testPhoto(640, 360), payload(), { _, _ -> }, "x") }
    }

    @Test fun flaggedOnlyWhenRangesAreMoreThanThreeApart() {
        assertFalse(flagged(Range(10, 12), Range(15, 20)))   // gap 3
        assertTrue(flagged(Range(10, 12), Range(16, 20)))    // gap 4
        assertFalse(flagged(Range(55, 65), Range(56, 74)))   // overlap
        assertTrue(flagged(Range(40, 50), Range(10, 12)))    // either direction
    }
}
