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
    private class Photo(val image: RgbImage, val count: CameraCount)

    private val photos = ArrayList<Photo>()
    private var club: String? = null
    private var clubTitle: String? = null
    private var hand: Result<Range>? = null
    private var locationOn = true

    val photoCount: Int get() = photos.size
    fun camera(): CameraCount = mergeEvent(photos.map { it.count })

    fun addPhoto(img: RgbImage) { photos += Photo(img, countPeople(img, detector)) }
    fun setClubUrl(text: String) { club = text.ifBlank { null } }
    fun setClubTitle(title: String?) { clubTitle = title?.ifBlank { null } }
    fun setHand(text: String) { hand = if (text.isBlank()) null else parseHandRange(text) }
    fun setLocationEnabled(on: Boolean) { locationOn = on }

    suspend fun export(drawOverlay: (RgbImage, List<String>) -> Unit): ExportOutcome {
        if (photos.isEmpty()) return ExportOutcome.Invalid("Take at least one photo first.")
        val handRange = hand?.getOrElse { return ExportOutcome.Invalid(it.message ?: "Check the number you counted.") }
            ?: return ExportOutcome.Invalid("Enter the number of people you counted, for example 55-65.")
        val url = normaliseClubUrl(club ?: return ExportOutcome.Invalid("Enter the club's web address.")).getOrElse { return ExportOutcome.Invalid(it.message ?: "Check the club address.") }

        val best = photos.maxBy { it.count.point }
        val blurred = blurHeads(best.image, best.count.boxesAtPoint, faces.detect(best.image))
        val merged = camera()
        val payload = Payload(
            eventId = newEventId(), utcSeconds = clockMs() / 1000, tz15 = tz15(offsetSeconds()),
            cell = location.current(locationOn), hand = handRange, camera = merged.range, cameraPoint = merged.point,
            photos = photos.size, installId = installId, url = url,
        )
        val result = composeExport(fitToExport(blurred), payload, drawOverlay, clubTitle ?: url)
        return ExportOutcome.Ready(encodeExportJpeg(result.image, encoder), result.image, payload, flagged(handRange, merged.range), result.report.weak)
    }
}
