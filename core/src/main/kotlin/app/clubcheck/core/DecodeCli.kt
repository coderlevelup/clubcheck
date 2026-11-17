package app.clubcheck.core

import java.io.File
import java.io.PrintStream
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/** What was read back out of a shared image. [payload] prefers the strip; the watermark is the cross-check. */
data class Decoded(val strip: Payload?, val watermark: Payload?) {
    val payload: Payload get() = strip ?: watermark!!
    val viaStrip: Boolean get() = strip != null
    val viaWatermark: Boolean get() = watermark != null
    val layersAgree: Boolean get() = strip != null && strip == watermark
}

private fun decodeCoded(coded: ByteArray): Payload? = ReedSolomon.decode(coded)?.let { Payload.decode(it) }

/**
 * Reads a shared image: the data strip first, then the hidden watermark. The payload length depends on the
 * club URL, so lengths are tried from the shortest possible up; Reed-Solomon plus the CRC make a wrong guess fail.
 * Images that are 16:9 but not 1280x720 (a chat app resized them) are rescaled first. Returns null if neither layer decodes.
 */
fun decodeExport(source: RgbImage): Decoded? {
    val img = when {
        source.width == EXPORT_WIDTH && source.height == EXPORT_HEIGHT -> source
        Math.abs(source.width * 9.0 / (source.height * 16.0) - 1.0) < 0.01 -> fitToExport(source)
        else -> return null
    }
    val shortest = Payload.FIXED_BYTES + 2 + ReedSolomon.PARITY
    val longest = StripCodec.capacityBytes(img.height)
    var strip: Payload? = null; var stripLength = -1
    for (len in shortest..longest) {
        strip = decodeCoded(StripCodec.read(img, len))
        if (strip != null) { stripLength = len; break }
    }
    var watermark: Payload? = null
    val lengths = if (stripLength > 0) listOf(stripLength) else (shortest..longest).toList()
    for (len in lengths) {
        watermark = decodeCoded(Watermark.read(img, len))
        if (watermark != null) break
    }
    return if (strip == null && watermark == null) null else Decoded(strip, watermark)
}

private fun json(d: Decoded): String {
    val p = d.payload
    return """{
  "url": "${p.url}",
  "hand": [${p.hand.lo}, ${p.hand.hi}],
  "camera": [${p.camera.lo}, ${p.camera.hi}],
  "cameraPoint": ${p.cameraPoint},
  "photos": ${p.photos},
  "utcSeconds": ${p.utcSeconds},
  "tz15": ${p.tz15},
  "area": ${cellLabel(p.cell)?.let { "\"$it\"" } ?: "null"},
  "event": "${p.eventId.joinToString("") { "%02x".format(it) }}",
  "viaStrip": ${d.viaStrip},
  "viaWatermark": ${d.viaWatermark},
  "layersAgree": ${d.layersAgree}
}"""
}

/** `decode <image>`: prints the data found in a shared ClubCheck image. Returns the exit code. */
fun runDecodeCli(args: Array<String>, out: PrintStream): Int {
    if (args.size != 1) { out.println("usage: decode <image.jpg|png>"); return 2 }
    val file = File(args[0])
    val image = try { ImageIO.read(file) } catch (e: Exception) { null }
    if (image == null) { out.println("""{"error": "could not read ${file.name} as an image"}"""); return 2 }
    val rgb = RgbImage(image.width, image.height, IntArray(image.width * image.height).also { image.getRGB(0, 0, image.width, image.height, it, 0, image.width) })
    val decoded = decodeExport(rgb)
    if (decoded == null) { out.println("""{"error": "no ClubCheck data found"}"""); return 1 }
    out.println(json(decoded))
    return 0
}

fun main(args: Array<String>) { exitProcess(runDecodeCli(args, System.out)) }
