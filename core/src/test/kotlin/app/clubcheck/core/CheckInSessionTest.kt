package app.clubcheck.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class CheckInSessionTest {
    /** Whole-image detections: the low byte of the first pixel says how many people to "find". */
    private val countFromPixel = PersonDetector { img, conf ->
        if (img.width != 1280 || img.height != 720) emptyList()
        else List(img.argb[0] and 0xFF) { i -> Box(60f + i * 90f, 100f, 120f + i * 90f, 400f, 0.9f) }.filter { it.score >= conf }
    }
    private val noFaces = FaceFinder { emptyList() }
    private val places = LocationProvider { enabled -> if (enabled) CoarseCell(-3390, 1840) else CoarseCell.NONE }
    private val encoder = JpegEncoder { img, q -> jpegBytes(img, q) }

    private fun session(detector: PersonDetector = countFromPixel, faces: FaceFinder = noFaces) = CheckInSession(
        detector, faces, places, encoder, clockMs = { 1_700_000_000_000L }, offsetSeconds = { 7200 },
        newEventId = { byteArrayOf(1, 2, 3, 4, 5, 6) }, installId = byteArrayOf(9, 8, 7, 6),
    )
    private fun photoWithPeople(n: Int, base: RgbImage = testPhoto()) = base.also { it.argb[0] = (0xFF shl 24) or n }
    private fun ready(o: ExportOutcome) = o as ExportOutcome.Ready
    private val noOverlay: (RgbImage, List<String>) -> Unit = { _, _ -> }

    @Test fun exportBlockedWithoutHandRange() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(3)); s.setClubUrl("club.example/x")
        assertTrue((s.export(noOverlay) as ExportOutcome.Invalid).reason.contains("counted"))
    }

    @Test fun exportBlockedWithoutClubUrl() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(3)); s.setHand("5")
        assertTrue(s.export(noOverlay) is ExportOutcome.Invalid)
    }

    @Test fun exportBlockedWithoutPhotos() = runBlocking {
        val s = session(); s.setHand("5"); s.setClubUrl("club.example/x")
        assertTrue((s.export(noOverlay) as ExportOutcome.Invalid).reason.contains("photo"))
    }

    @Test fun reversedAndLongInputsGiveClearReasons() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(3)); s.setHand("65-55")
        s.setClubUrl("club.example/" + "a".repeat(80))
        assertTrue((s.export(noOverlay) as ExportOutcome.Invalid).reason.contains("65"))
        s.setClubUrl("club.example/x")
        assertEquals(Range(55, 65), ready(s.export(noOverlay)).payload.hand)
    }

    @Test fun twoPhotosUseMaxCameraCount() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(4, testPhoto(seed = 1))); s.addPhoto(photoWithPeople(7, testPhoto(seed = 2)))
        s.setHand("7"); s.setClubUrl("club.example/x")
        val r = ready(s.export(noOverlay))
        assertEquals(7, r.payload.cameraPoint); assertEquals(2, r.payload.photos)
    }

    @Test fun locationOffExportsWithoutArea() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(3)); s.setHand("3"); s.setClubUrl("club.example/x"); s.setLocationEnabled(false)
        val lines = ArrayList<String>()
        val r = ready(s.export { _, l -> lines += l })
        assertEquals(CoarseCell.NONE, r.payload.cell)
        assertFalse(lines[1].contains("S 18"))
    }

    @Test fun locationOnCarriesTheCoarseCell() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(3)); s.setHand("3"); s.setClubUrl("club.example/x")
        assertEquals(CoarseCell(-3390, 1840), ready(s.export(noOverlay)).payload.cell)
    }

    @Test fun weakWatermarkIsSurfacedAndStripStillDecodes() = runBlocking {
        val white = RgbImage(1280, 720, IntArray(1280 * 720) { -1 })
        val s = session(); s.addPhoto(photoWithPeople(2, white)); s.setHand("2"); s.setClubUrl("club.example/x")
        val r = ready(s.export(noOverlay))
        assertTrue(r.weakWatermark)
        val coded = ReedSolomon.encode(r.payload.encode()).size
        assertEquals(r.payload, Payload.decode(ReedSolomon.decode(StripCodec.read(r.image, coded))!!))
    }

    @Test fun headsOfDetectedPeopleAreBlurredInTheExport() = runBlocking {
        val photo = photoWithPeople(0, testPhoto(seed = 6))
        val detector = PersonDetector { img, c -> if (img.width == 1280) listOf(Box(500f, 100f, 600f, 400f, 0.9f)).filter { it.score >= c } else emptyList() }
        val s = session(detector); s.addPhoto(photo); s.setHand("1"); s.setClubUrl("club.example/x")
        val r = ready(s.export(noOverlay))
        val e = headEllipse(Box(500f, 100f, 600f, 400f, 0.9f))
        fun roughness(img: RgbImage): Double {
            val l = img.luma(); var sum = 0.0; var n = 0
            for (y in (e.cy - e.ry / 2).toInt() until (e.cy + e.ry / 2).toInt()) for (x in (e.cx - e.rx / 2).toInt() until (e.cx + e.rx / 2).toInt()) { sum += Math.abs(l[y * 1280 + x] - l[y * 1280 + x + 1]); n++ }
            return sum / n
        }
        assertTrue(roughness(r.image) < roughness(photo) * 0.5, "pixel noise inside the head should be smoothed away")
    }

    @Test fun facesFromTheFaceFinderAreBlurredToo() = runBlocking {
        val photo = photoWithPeople(0, testPhoto(seed = 7))
        val faces = FaceFinder { listOf(Box(700f, 300f, 760f, 360f, 1f)) }
        val s = session(faces = faces); s.addPhoto(photo); s.setHand("1"); s.setClubUrl("club.example/x")
        val r = ready(s.export(noOverlay))
        val e = faceEllipse(Box(700f, 300f, 760f, 360f, 1f))
        fun rough(img: RgbImage): Double { val l = img.luma(); var t = 0.0; var n = 0
            for (y in (e.cy - 10).toInt()..(e.cy + 10).toInt()) for (x in (e.cx - 10).toInt()..(e.cx + 10).toInt()) { t += Math.abs(l[y * 1280 + x] - l[y * 1280 + x + 1]); n++ }
            return t / n }
        assertTrue(rough(r.image) < rough(photo) * 0.5)
    }

    @Test fun onlyTheBestPhotoIsKeptInMemory() = runBlocking {
        val s = session()
        s.addPhoto(photoWithPeople(2, testPhoto(seed = 1))); s.addPhoto(photoWithPeople(9, testPhoto(seed = 2))); s.addPhoto(photoWithPeople(4, testPhoto(seed = 3)))
        assertEquals(1, s.retainedImages); assertEquals(3, s.photoCount); assertEquals(9, s.camera().point)
    }

    @Test fun aPersonSeenOnlyAtLowConfidenceStillGetsABlurredHead() = runBlocking {
        val photo = photoWithPeople(0, testPhoto(seed = 8))
        val faint = PersonDetector { img, c -> if (img.width == 1280) listOf(Box(500f, 100f, 600f, 400f, 0.2f)).filter { it.score >= c } else emptyList() }
        val s = session(faint); s.addPhoto(photo); s.setHand("0"); s.setClubUrl("club.example/x")
        val r = ready(s.export(noOverlay))
        val e = headEllipse(Box(500f, 100f, 600f, 400f, 0.2f))
        fun rough(img: RgbImage): Double { val l = img.luma(); var t = 0.0; var n = 0
            for (y in (e.cy - e.ry / 2).toInt() until (e.cy + e.ry / 2).toInt()) for (x in (e.cx - e.rx / 2).toInt() until (e.cx + e.rx / 2).toInt()) { t += Math.abs(l[y * 1280 + x] - l[y * 1280 + x + 1]); n++ }
            return t / n }
        assertTrue(rough(r.image) < rough(photo) * 0.5)
    }

    @Test fun faceFinderFailureRefusesTheExport() = runBlocking {
        val s = session(faces = FaceFinder { throw IllegalStateException("ML Kit failed") })
        s.addPhoto(photoWithPeople(2)); s.setHand("2"); s.setClubUrl("club.example/x")
        val out = s.export(noOverlay)
        assertTrue(out is ExportOutcome.Invalid, "never export when the face check could not run")
        assertTrue((out as ExportOutcome.Invalid).reason.contains("faces"))
    }

    @Test fun changingTheUrlDropsTheOldTitle() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(2)); s.setHand("2")
        s.setClubUrl("club.example/a"); s.setClubTitle("Chess Club", "club.example/a")
        s.setClubUrl("other.example/b")
        val lines = ArrayList<String>(); s.export { _, l -> lines += l }
        assertEquals("OTHER.EXAMPLE/B", lines[0])
    }

    @Test fun aTitleFetchedForAnEarlierUrlIsIgnored() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(2)); s.setHand("2"); s.setClubUrl("club.example/b")
        s.setClubTitle("Old club", "club.example/a")          // a slow fetch for what was typed before
        val lines = ArrayList<String>(); s.export { _, l -> lines += l }
        assertEquals("CLUB.EXAMPLE/B", lines[0])
    }

    @Test fun eventTimeIsWhenTheFirstPhotoWasTaken() = runBlocking {
        var now = 1_700_000_000_000L
        val s = CheckInSession(countFromPixel, noFaces, places, encoder, clockMs = { now }, offsetSeconds = { 0 },
            newEventId = { byteArrayOf(1, 2, 3, 4, 5, 6) }, installId = byteArrayOf(1, 2, 3, 4))
        s.addPhoto(photoWithPeople(2)); now += 600_000          // ten minutes of filling in the form
        s.setHand("2"); s.setClubUrl("club.example/x")
        assertEquals(1_700_000_000L, ready(s.export(noOverlay)).payload.utcSeconds)
    }

    @Test fun flaggedWhenRangesAreFarApart() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(20)); s.setHand("5-6"); s.setClubUrl("club.example/x")
        assertTrue(ready(s.export(noOverlay)).flagged)
    }

    @Test fun exportDecodesAfterJpegEndToEnd() = runBlocking {
        val s = session(); s.addPhoto(photoWithPeople(5)); s.setHand("4-6"); s.setClubUrl("https://Club.Example/Chess/"); s.setClubTitle("Chess Club", "club.example/Chess")
        val r = ready(s.export(noOverlay))
        val back = ImageIO.read(ByteArrayInputStream(r.jpeg)).toRgb()
        val coded = ReedSolomon.encode(r.payload.encode()).size
        val decoded = Payload.decode(ReedSolomon.decode(StripCodec.read(back, coded))!!)!!
        assertEquals("club.example/Chess", decoded.url); assertEquals(Range(4, 6), decoded.hand); assertEquals(5, decoded.cameraPoint)
        assertEquals(1_700_000_000L, decoded.utcSeconds); assertEquals(8, decoded.tz15)
    }
}
