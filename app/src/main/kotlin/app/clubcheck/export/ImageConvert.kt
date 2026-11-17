package app.clubcheck.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import app.clubcheck.core.JpegEncoder
import app.clubcheck.core.RgbImage
import app.clubcheck.core.applyOrientation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun RgbImage.toBitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.setPixels(argb, 0, width, 0, 0, width, height) }

fun Bitmap.toRgbImage(): RgbImage {
    val px = IntArray(width * height); getPixels(px, 0, width, 0, 0, width, height)
    return RgbImage(width, height, px)
}

/** Decodes a camera JPEG (longest side limited to about [maxSide]) and rotates it upright using its EXIF orientation. */
fun decodeUpright(bytes: ByteArray, maxSide: Int = 2560): RgbImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("Could not read the photo")
    val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rgb = bmp.toRgbImage(); bmp.recycle()
    return applyOrientation(rgb, orientation)
}

/** `Bitmap.compress` writes pixels only: no EXIF, GPS or thumbnail. */
object BitmapJpegEncoder : JpegEncoder {
    override fun encode(img: RgbImage, quality: Int): ByteArray {
        val bmp = img.toBitmap()
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out); bmp.recycle()
        return out.toByteArray()
    }
}
