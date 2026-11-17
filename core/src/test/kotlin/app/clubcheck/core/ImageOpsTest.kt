package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImageOpsTest {
    /** 2 wide x 3 high: [1 2; 3 4; 5 6]. */
    private val src = RgbImage(2, 3, intArrayOf(1, 2, 3, 4, 5, 6))
    private fun ints(img: RgbImage) = img.argb.toList()

    @Test fun orientation1AndUnknownAreIdentity() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ints(applyOrientation(src, 1)))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ints(applyOrientation(src, 0)))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ints(applyOrientation(src, 99)))
    }

    @Test fun orientation6RotatesNinetyClockwise() {
        val r = applyOrientation(src, 6)
        assertEquals(3, r.width); assertEquals(2, r.height)
        assertEquals(listOf(5, 3, 1, 6, 4, 2), ints(r))
    }

    @Test fun orientation8RotatesNinetyCounterClockwise() {
        val r = applyOrientation(src, 8)
        assertEquals(3, r.width); assertEquals(2, r.height)
        assertEquals(listOf(2, 4, 6, 1, 3, 5), ints(r))
    }

    @Test fun orientation3RotatesHalfTurnAnd2And4Flip() {
        assertEquals(listOf(6, 5, 4, 3, 2, 1), ints(applyOrientation(src, 3)))
        assertEquals(listOf(2, 1, 4, 3, 6, 5), ints(applyOrientation(src, 2)))
        assertEquals(listOf(5, 6, 3, 4, 1, 2), ints(applyOrientation(src, 4)))
    }

    @Test fun orientation5And7Transpose() {
        assertEquals(listOf(1, 3, 5, 2, 4, 6), ints(applyOrientation(src, 5)))
        assertEquals(listOf(6, 4, 2, 5, 3, 1), ints(applyOrientation(src, 7)))
    }

    private fun solid(w: Int, h: Int, c: Int) = RgbImage(w, h, IntArray(w * h) { c })
    private val red = 0xFFFF0000.toInt(); private val black = 0xFF000000.toInt()

    @Test fun sixteenByNineFillsTheExportFrame() {
        val out = fitToExport(solid(1600, 900, red))
        assertEquals(1280, out.width); assertEquals(720, out.height)
        assertEquals(red, out.argb[0]); assertEquals(red, out.argb[out.argb.size - 1])
    }

    @Test fun fourByThreeGetsSideBars() {
        val out = fitToExport(solid(1200, 900, red))          // scales to 960x720, bars of 160px
        assertEquals(black, out.argb[360 * 1280 + 100]); assertEquals(red, out.argb[360 * 1280 + 640]); assertEquals(black, out.argb[360 * 1280 + 1200])
    }

    @Test fun portraitGetsWideBars() {
        val out = fitToExport(solid(900, 1600, red))          // 405x720 centred
        assertEquals(black, out.argb[360 * 1280 + 100]); assertEquals(red, out.argb[360 * 1280 + 640])
    }

    @Test fun downscalingAveragesInsteadOfAliasing() {
        val checker = RgbImage(2560, 1440, IntArray(2560 * 1440) { if ((it % 2560 + it / 2560) % 2 == 0) -1 else black })
        val mid = fitToExport(checker).argb[360 * 1280 + 640] and 0xFF
        assertTrue(mid in 100..155, "expected a mid grey, got $mid")
    }
}
