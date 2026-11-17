package app.clubcheck.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.clubcheck.core.EXPORT_HEIGHT
import app.clubcheck.core.FOOTER_LINE_PX
import app.clubcheck.core.REPO_FOOTER
import app.clubcheck.core.RgbImage
import app.clubcheck.core.wrapLine

/**
 * Draws the header (title, readable club link, time and area, counts) and the repo footer onto the export image.
 * Long lines, such as a long web link, are word-wrapped, and the header panel grows to fit. The repo line sits
 * directly above the contained data strip, which takes [reservedBottom] px at the bottom and is labelled by that line.
 * Verified on a device, not on the JVM.
 */
object OverlayRenderer {
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    private val sizes = floatArrayOf(26f, 19f, 19f, 19f)
    private val colours = intArrayOf(Color.rgb(233, 241, 236), Color.rgb(233, 241, 236), Color.rgb(159, 217, 191), Color.rgb(159, 217, 191))

    private fun paint(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = mono }

    fun draw(img: RgbImage, lines: List<String>, reservedBottom: Int) {
        val bmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(img.argb, 0, img.width, 0, 0, img.width, img.height)
        val canvas = Canvas(bmp)
        val left = 10f
        val textLeft = left + 12f
        val textWidth = img.width - 10f - textLeft - 12f
        val footerTop = (EXPORT_HEIGHT - reservedBottom - FOOTER_LINE_PX).toFloat()

        // lay out every wrapped line first, so the panel can be drawn behind them at the right height
        class Row(val text: String, val paint: Paint, val baseline: Float)
        val rows = ArrayList<Row>()
        var y = 10f + 8f
        lines.forEachIndexed { i, line ->
            val p = paint(sizes[i.coerceAtMost(sizes.lastIndex)], colours[i.coerceAtMost(colours.lastIndex)])
            val charWidth = p.measureText("M")
            val maxChars = (textWidth / charWidth).toInt().coerceAtLeast(8)
            for (part in wrapLine(line, maxChars)) {
                y += p.textSize * 1.2f
                if (y < footerTop - 8f) rows += Row(part, p, y)
            }
        }
        val panelBottom = minOf(y + 8f, footerTop - 4f)
        canvas.drawRect(left, 10f, img.width - 10f, panelBottom, Paint().apply { color = Color.argb(160, 0, 0, 0) })
        rows.forEach { canvas.drawText(it.text, textLeft, it.baseline, it.paint) }

        // the repo line: opaque, directly above the strip it labels
        canvas.drawRect(0f, footerTop, img.width.toFloat(), footerTop + FOOTER_LINE_PX, Paint().apply { color = Color.BLACK })
        canvas.drawText(REPO_FOOTER, 16f, footerTop + FOOTER_LINE_PX - 12f, paint(19f, Color.rgb(233, 241, 236)))
        bmp.getPixels(img.argb, 0, img.width, 0, 0, img.width, img.height)
        bmp.recycle()
    }
}
