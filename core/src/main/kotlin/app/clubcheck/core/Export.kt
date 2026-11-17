package app.clubcheck.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val EXPORT_WIDTH = 1280
const val EXPORT_HEIGHT = 720
const val REPO_FOOTER = "github.com/coderlevelup/clubcheck"

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)

private fun rangeText(r: Range) = if (r.lo == r.hi) "${r.lo}" else "${r.lo}-${r.hi}"

private fun offsetText(zone: ZoneOffset): String {
    val total = zone.totalSeconds
    val sign = if (total < 0) "-" else "+"
    val h = Math.abs(total) / 3600; val m = Math.abs(total) % 3600 / 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

/** The three visible overlay lines: title, local date and time with the coarse area, and both counts. */
fun overlayLines(p: Payload, title: String, zone: ZoneOffset): List<String> {
    val local = Instant.ofEpochSecond(p.utcSeconds).atOffset(zone).format(dateFormat).uppercase(Locale.ENGLISH)
    val area = cellLabel(p.cell)
    val whenWhere = "$local ${offsetText(zone)}" + if (area != null) "   $area" else ""
    val verdict = if (flagged(p.hand, p.camera)) "CHECK COUNTS" else "RANGES OVERLAP"
    return listOf(title.uppercase(Locale.ENGLISH), whenWhere, "HAND ${rangeText(p.hand)}  CAMERA ${rangeText(p.camera)}  $verdict")
}

data class ExportResult(val image: RgbImage, val report: EmbedReport)

/**
 * Builds the shareable image from an already blurred 1280x720 photo: overlay (drawn by [drawOverlay]),
 * then the data strip, then the hidden watermark last. The input is not modified.
 */
fun composeExport(blurred: RgbImage, p: Payload, drawOverlay: (RgbImage, List<String>) -> Unit, title: String): ExportResult {
    require(blurred.width == EXPORT_WIDTH && blurred.height == EXPORT_HEIGHT) { "export image must be ${EXPORT_WIDTH}x$EXPORT_HEIGHT" }
    val img = blurred.copy()
    drawOverlay(img, overlayLines(p, title, ZoneOffset.ofTotalSeconds(p.tz15 * 900)))
    val coded = ReedSolomon.encode(p.encode())
    StripCodec.draw(img, coded)
    val report = Watermark.embed(img, coded)
    return ExportResult(img, report)
}

/** Turns pixels into JPEG bytes. On Android this is `Bitmap.compress`; both write no metadata. */
fun interface JpegEncoder { fun encode(img: RgbImage, quality: Int): ByteArray }

/**
 * Encodes the export. The bytes come only from the pixels of [export]: nothing from the camera file
 * (EXIF, GPS, orientation, thumbnails, XMP) is ever copied across.
 */
fun encodeExportJpeg(export: RgbImage, encoder: JpegEncoder, quality: Int = 92): ByteArray {
    require(export.width == EXPORT_WIDTH && export.height == EXPORT_HEIGHT) { "export image must be ${EXPORT_WIDTH}x$EXPORT_HEIGHT" }
    return encoder.encode(export, quality)
}
