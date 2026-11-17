package app.clubcheck.core

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt
import kotlin.random.Random

fun RgbImage.toBuffered(): BufferedImage {
    val b = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    b.setRGB(0, 0, width, height, argb, 0, width)
    return b
}

fun BufferedImage.toRgb(): RgbImage {
    val out = IntArray(width * height)
    getRGB(0, 0, width, height, out, 0, width)
    return RgbImage(width, height, IntArray(out.size) { out[it] or (0xFF shl 24) })
}

private fun scaled(src: BufferedImage, w: Int, h: Int): BufferedImage {
    val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = dst.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
    g.dispose()
    return dst
}

fun jpegBytes(img: RgbImage, quality: Int): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val param = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality / 100f
    }
    val out = ByteArrayOutputStream()
    writer.output = ImageIO.createImageOutputStream(out)
    writer.write(null, IIOImage(img.toBuffered(), null, null), param)
    writer.dispose()
    return out.toByteArray()
}

/** Encodes as JPEG at [quality], optionally after shrinking by [scale], and returns it at the original size again. */
fun jpegRoundTrip(img: RgbImage, quality: Int, scale: Double = 1.0): RgbImage {
    var src = img.toBuffered()
    if (scale != 1.0) src = scaled(src, (img.width * scale).roundToInt(), (img.height * scale).roundToInt())
    val bytes = jpegBytes(src.toRgb(), quality)
    var back = ImageIO.read(ByteArrayInputStream(bytes))
    if (scale != 1.0) back = scaled(back, img.width, img.height)
    return back.toRgb()
}

/** A repeatable photo-like test image: smooth gradients, blobs and noise. [brightness] shifts overall level. */
fun testPhoto(width: Int = 1280, height: Int = 720, seed: Int = 1, brightness: Int = 0): RgbImage {
    val rnd = Random(seed)
    val px = IntArray(width * height)
    val blobs = List(14) { Triple(rnd.nextInt(width), rnd.nextInt(height), 40 + rnd.nextInt(160)) }
    for (y in 0 until height) for (x in 0 until width) {
        var v = 70.0 + 50.0 * x / width + 30.0 * y / height + brightness
        for ((bx, by, r) in blobs) {
            val d = Math.hypot((x - bx).toDouble(), (y - by).toDouble())
            if (d < r) v += 60.0 * (1 - d / r) * (if (bx % 2 == 0) 1 else -1)
        }
        v += rnd.nextInt(-6, 7)
        val c = v.coerceIn(0.0, 255.0).toInt()
        px[y * width + x] = (0xFF shl 24) or (c shl 16) or ((c * 9 / 10) shl 8) or (c * 8 / 10)
    }
    return RgbImage(width, height, px)
}
