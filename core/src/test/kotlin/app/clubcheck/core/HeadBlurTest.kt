package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HeadBlurTest {
    private fun checker(w: Int = 400, h: Int = 400) = RgbImage(w, h, IntArray(w * h) { if ((it % w + it / w) % 2 == 0) -1 else 0xFF000000.toInt() })
    private fun lumaVariance(img: RgbImage, inside: (Int, Int) -> Boolean): Double {
        val l = img.luma(); val vals = ArrayList<Double>()
        for (y in 0 until img.height) for (x in 0 until img.width) if (inside(x, y)) vals += l[y * img.width + x].toDouble()
        val mean = vals.average(); return vals.sumOf { (it - mean) * (it - mean) } / vals.size
    }

    @Test fun headEllipseMatchesSpikeFormula() {
        val e = headEllipse(Box(0f, 0f, 100f, 400f, 0.9f))   // d = max(min(50, 88), 28) = 50
        assertEquals(50f, e.cx, 1e-3f); assertEquals(27.5f, e.cy, 1e-3f)
        assertEquals(37.5f, e.rx, 1e-3f); assertEquals(40f, e.ry, 1e-3f); assertEquals(25f, e.sigma, 1e-3f)
    }

    @Test fun tinyBoxUsesMinimumDiameter() {
        val e = headEllipse(Box(0f, 0f, 20f, 40f, 0.9f))      // d would be 8.8, so 28
        assertEquals(0.75f * 28f, e.rx, 1e-3f); assertEquals(0.8f * 28f, e.ry, 1e-3f); assertEquals(14f, e.sigma, 1e-3f)
        assertEquals(0.55f * 28f, e.cy, 1e-3f)
    }

    @Test fun faceEllipseMatchesFormula() {
        val e = faceEllipse(Box(100f, 100f, 160f, 150f, 0.5f))  // d = max(60, 50, 24) = 60
        assertEquals(130f, e.cx, 1e-3f); assertEquals(125f, e.cy, 1e-3f)
        assertEquals(51f, e.rx, 1e-3f); assertEquals(57f, e.ry, 1e-3f); assertEquals(27f, e.sigma, 1e-3f)
        assertEquals(10.8f, faceEllipse(Box(0f, 0f, 10f, 10f, 1f)).sigma, 1e-3f)  // tiny face: d is floored at 24
    }

    @Test fun blurChangesPixelsInsideNotFarOutside() {
        val src = checker(); val person = Box(150f, 100f, 250f, 400f, 0.9f)
        val e = headEllipse(person)
        val out = blurHeads(src, listOf(person), emptyList())
        val core = { x: Int, y: Int -> Math.hypot((x - e.cx) / (0.5 * e.rx), (y - e.cy) / (0.5 * e.ry)) < 1.0 }
        val before = lumaVariance(src, core); val after = lumaVariance(out, core)
        assertTrue(after <= before * 0.1, "variance should drop by 90%: $before -> $after")
        for ((x, y) in listOf(5 to 395, 395 to 395, 5 to 5)) assertEquals(src.argb[y * 400 + x], out.argb[y * 400 + x], "far pixel $x,$y")
    }

    @Test fun facesAreBlurredToo() {
        val src = checker(); val face = Box(40f, 250f, 100f, 310f, 0.4f)
        val e = faceEllipse(face)
        val out = blurHeads(src, emptyList(), listOf(face))
        val core = { x: Int, y: Int -> Math.hypot((x - e.cx) / (0.5 * e.rx), (y - e.cy) / (0.5 * e.ry)) < 1.0 }
        assertTrue(lumaVariance(out, core) <= lumaVariance(src, core) * 0.1)
    }

    @Test fun blurDoesNotMutateInput() {
        val src = checker(); val snapshot = src.argb.copyOf()
        blurHeads(src, listOf(Box(150f, 100f, 250f, 400f, 0.9f)), listOf(Box(40f, 250f, 100f, 310f, 0.4f)))
        assertArrayEquals(snapshot, src.argb)
    }

    @Test fun boxesPartlyOutsideTheImageDoNotCrash() {
        val out = blurHeads(checker(), listOf(Box(-50f, -30f, 40f, 200f, 0.9f), Box(380f, 380f, 520f, 700f, 0.9f)), emptyList())
        assertEquals(400, out.width)
    }

    @Test fun noBoxesReturnsIdenticalCopy() {
        val src = checker(); val out = blurHeads(src, emptyList(), emptyList())
        assertArrayEquals(src.argb, out.argb); assertNotSame(src, out)
    }
}
