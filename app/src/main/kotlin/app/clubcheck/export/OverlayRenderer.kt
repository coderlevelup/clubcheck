package app.clubcheck.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.clubcheck.core.EXPORT_HEIGHT
import app.clubcheck.core.REPO_FOOTER
import app.clubcheck.core.RgbImage
import app.clubcheck.core.StripCodec

/** Draws the text overlay and the repo footer onto an export-sized image (verified on a device, not on the JVM). */
object OverlayRenderer {
    fun draw(img: RgbImage, lines: List<String>) {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(img.argb, 0, img.width, 0, 0, img.width, img.height)
        val canvas = Canvas(bmp)
        val left = StripCodec.WIDTH_PX + 10f
        val panel = Paint().apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRect(left, 10f, img.width - 10f, 112f, panel)
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        fun text(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = mono }
        val ys = floatArrayOf(44f, 76f, 102f)
        val sizes = floatArrayOf(26f, 19f, 19f)
        val colours = intArrayOf(Color.rgb(233, 241, 236), Color.rgb(159, 217, 191), Color.rgb(159, 217, 191))
        lines.take(3).forEachIndexed { i, s -> canvas.drawText(s, left + 12f, ys[i], text(sizes[i], colours[i])) }
        canvas.drawRect(left - 10f, EXPORT_HEIGHT - 40f, img.width.toFloat(), EXPORT_HEIGHT.toFloat(), Paint().apply { color = Color.argb(185, 0, 0, 0) })
        canvas.drawText(REPO_FOOTER, left + 4f, EXPORT_HEIGHT - 14f, text(19f, Color.rgb(233, 241, 236)))
        bmp.getPixels(img.argb, 0, img.width, 0, 0, img.width, img.height)
        bmp.recycle()
    }
}
