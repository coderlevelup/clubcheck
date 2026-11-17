package app.clubcheck.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val EXPORT_WIDTH = 1280
const val EXPORT_HEIGHT = 720
const val REPO_FOOTER = "github.com/coderlevelup/clubcheck"
const val FOOTER_LINE_PX = 40

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)

private fun rangeText(r: Range) = if (r.lo == r.hi) "${r.lo}" else "${r.lo}-${r.hi}"

private fun offsetText(zone: ZoneOffset): String {
    val total = zone.totalSeconds
    val sign = if (total < 0) "-" else "+"
    val h = Math.abs(total) / 3600; val m = Math.abs(total) % 3600 / 60
    return "UTC$sign$h" + if (m != 0) ":%02d".format(m) else ""
}

/**
 * Draws the header and the repo line onto the export image. The Int is the height reserved at the bottom for the
 * contained data strip (band plus margin); the repo line belongs directly above it, [FOOTER_LINE_PX] tall.
 */
typealias OverlayDraw = (RgbImage, List<String>, Int) -> Unit

/**
 * The visible header lines: the club title, the club link in readable form (skipped when the title already is the
 * link), local date and time with the coarse area, and both counts.
 */
fun overlayLines(p: Payload, title: String, zone: ZoneOffset): List<String> {
    val local = Instant.ofEpochSecond(p.utcSeconds).atOffset(zone).format(dateFormat).uppercase(Locale.ENGLISH)
    val area = cellLabel(p.cell)
    val whenWhere = "$local ${offsetText(zone)}" + if (area != null) "   $area" else ""
    val verdict = if (flagged(p.hand, p.camera)) "CHECK COUNTS" else "RANGES OVERLAP"
    val link = if (title.equals(p.url, ignoreCase = true)) emptyList() else listOf(p.url)
    return listOf(title.uppercase(Locale.ENGLISH)) + link + listOf(whenWhere, "HAND ${rangeText(p.hand)}  CAMERA ${rangeText(p.camera)}  $verdict")
}

data class ExportResult(val image: RgbImage, val report: EmbedReport)

/**
 * Builds the shareable image from an already blurred photo of any size. Down the page: the photo (fitted into the
 * space that is left), a line for the repo URL, and below it the contained data strip that the repo URL labels, so
 * the strip never covers the picture. The header is drawn by [drawOverlay]; the hidden watermark goes in last.
 * The input is not modified.
 */
fun composeExport(blurred: RgbImage, p: Payload, drawOverlay: OverlayDraw, title: String): ExportResult {
    val coded = ReedSolomon.encode(p.encode())
    val reserved = StripCodec.reservedPx(coded.size, EXPORT_WIDTH)
    val photo = fitInto(blurred, EXPORT_WIDTH, EXPORT_HEIGHT - FOOTER_LINE_PX - reserved)
    val img = RgbImage(EXPORT_WIDTH, EXPORT_HEIGHT, IntArray(EXPORT_WIDTH * EXPORT_HEIGHT) { 0xFF000000.toInt() })
    System.arraycopy(photo.argb, 0, img.argb, 0, photo.argb.size)
    drawOverlay(img, overlayLines(p, title, ZoneOffset.ofTotalSeconds(p.tz15 * 900)), reserved)
    StripCodec.draw(img, coded)
    return ExportResult(img, Watermark.embed(img, coded))
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
