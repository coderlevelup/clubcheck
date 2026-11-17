package app.clubcheck.core

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO

/** Dev tool, skipped unless PREVIEW_OUT (a folder) and PREVIEW_PHOTO (an already blurred photo) are set. */
class ExportPreviewTest {
    private fun awtOverlay(img: RgbImage, lines: List<String>, reservedBottom: Int) {
        val b = img.toBuffered(); val g = b.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val left = 10; val textLeft = left + 12; val textWidth = img.width - 10 - textLeft - 12
        class Row(val text: String, val font: Font, val color: Color, val y: Int)
        val rows = ArrayList<Row>(); var y = 18
        lines.forEachIndexed { i, line ->
            val size = if (i == 0) 26 else 19
            val font = Font(Font.MONOSPACED, Font.PLAIN, size); val charW = g.getFontMetrics(font).charWidth('M')
            val color = if (i <= 1 || lines.size == 3 && i == 0) Color(233, 241, 236) else Color(159, 217, 191)
            for (part in wrapLine(line, maxOf(8, textWidth / charW))) { y += (size * 1.2).toInt(); rows += Row(part, font, color, y) }
        }
        g.color = Color(0, 0, 0, 160); g.fillRect(left, 10, img.width - 10 - left, y + 8 - 10)
        rows.forEach { g.font = it.font; g.color = it.color; g.drawString(it.text, textLeft, it.y) }
        val footerTop = EXPORT_HEIGHT - reservedBottom - FOOTER_LINE_PX
        g.color = Color.BLACK; g.fillRect(0, footerTop, img.width, FOOTER_LINE_PX)
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 19); g.color = Color(233, 241, 236); g.drawString(REPO_FOOTER, 16, footerTop + FOOTER_LINE_PX - 12)
        g.dispose()
        b.getRGB(0, 0, img.width, img.height, img.argb, 0, img.width)
    }

    @Test fun writesPreviewImages() {
        val out = System.getenv("PREVIEW_OUT"); val photoPath = System.getenv("PREVIEW_PHOTO")
        assumeTrue(out != null && photoPath != null)
        val photo = ImageIO.read(File(photoPath)).toRgb()
        for ((name, url) in listOf("short" to "rsvp.withgoogle.com/events/android-developer-day-capetown",
                                   "long" to "rsvp.withgoogle.com/events/android-developer-day-capetown/sessions/jetpack-compose-first-and-after")) {
            val p = Payload(byteArrayOf(1, 2, 3, 4, 5, 6), 1794905621L, 8, CoarseCell(-3390, 1840), Range(55, 65), Range(39, 44), 41, 1, byteArrayOf(1, 2, 3, 4), url)
            val img = composeExport(photo, p, ::awtOverlay, "Android Developer Day Cape Town").image
            ImageIO.write(img.toBuffered(), "png", File(out, "preview-$name.png"))
        }
    }
}
