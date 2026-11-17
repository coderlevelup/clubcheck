package app.clubcheck.core

import java.security.SecureRandom

fun interface FaceFinder { fun detect(img: RgbImage): List<Box> }
fun interface LocationProvider { suspend fun current(enabled: Boolean): CoarseCell }

sealed interface ExportOutcome {
    data class Ready(val jpeg: ByteArray, val image: RgbImage, val payload: Payload, val flagged: Boolean, val weakWatermark: Boolean) : ExportOutcome
    data class Invalid(val reason: String) : ExportOutcome
}

/**
 * One check-in: photos in, one shareable export out. Counts happen when a photo is added; the export is
 * built from the photo with the highest camera estimate, blurred before anything else happens to it.
 */
class CheckInSession(
    private val detector: PersonDetector,
    private val faces: FaceFinder,
    private val location: LocationProvider,
    private val encoder: JpegEncoder,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val offsetSeconds: () -> Int = { java.util.TimeZone.getDefault().getOffset(clockMs()) / 1000 },
    private val newEventId: () -> ByteArray = { ByteArray(6).also { SecureRandom().nextBytes(it) } },
    private val installId: ByteArray,
) {
    /** Only the counts of every photo are kept; the pixels of just the best one (highest estimate) stay in memory. */
    private val counts = ArrayList<CameraCount>()
    private var bestImage: RgbImage? = null
    private var bestCount: CameraCount? = null
    private var firstPhotoMs: Long? = null
    private var club: String? = null
    private var clubTitle: String? = null
    private var titleUrl: String? = null
    private var hand: Result<Range>? = null
    private var locationOn = true

    val photoCount: Int get() = counts.size
    val retainedImages: Int get() = if (bestImage != null) 1 else 0
    fun camera(): CameraCount = mergeEvent(counts)

    fun addPhoto(img: RgbImage) {
        if (firstPhotoMs == null) firstPhotoMs = clockMs()
        val count = countPeople(img, detector)
        counts += count
        if (bestCount == null || count.point > bestCount!!.point) { bestImage = img; bestCount = count }
    }

    fun setClubUrl(text: String) {
        club = text.ifBlank { null }
        if (normaliseClubUrl(text).getOrNull() != titleUrl) clubTitle = null
    }

    /** [forUrl] is the normalised address the title was fetched for; a title for an address no longer typed is dropped. */
    fun setClubTitle(title: String?, forUrl: String) {
        if (normaliseClubUrl(club ?: return).getOrNull() == forUrl) { clubTitle = title?.ifBlank { null }; titleUrl = forUrl }
    }

    fun setHand(text: String) { hand = if (text.isBlank()) null else parseHandRange(text) }
    fun setLocationEnabled(on: Boolean) { locationOn = on }

    suspend fun export(drawOverlay: OverlayDraw): ExportOutcome {
        val image = bestImage; val count = bestCount
        if (image == null || count == null) return ExportOutcome.Invalid("Take at least one photo first.")
        val handRange = hand?.getOrElse { return ExportOutcome.Invalid(it.message ?: "Check the number you counted.") }
            ?: return ExportOutcome.Invalid("Enter the number of people you counted, for example 55-65.")
        val url = normaliseClubUrl(club ?: return ExportOutcome.Invalid("Enter the club's web address.")).getOrElse { return ExportOutcome.Invalid(it.message ?: "Check the club address.") }

        val found = try { faces.detect(image) } catch (e: Exception) {
            return ExportOutcome.Invalid("Could not check the photo for faces, so nothing was exported. Please try again.")
        }
        val blurred = blurHeads(image, count.blurBoxes, found)
        val merged = camera()
        val payload = Payload(
            eventId = newEventId(), utcSeconds = (firstPhotoMs ?: clockMs()) / 1000, tz15 = tz15(offsetSeconds()),
            cell = location.current(locationOn), hand = handRange, camera = merged.range, cameraPoint = merged.point,
            photos = counts.size, installId = installId, url = url,
        )
        val result = composeExport(blurred, payload, drawOverlay, clubTitle?.takeIf { titleUrl == url } ?: url)
        return ExportOutcome.Ready(encodeExportJpeg(result.image, encoder), result.image, payload, flagged(handRange, merged.range), result.report.weak)
    }
}
