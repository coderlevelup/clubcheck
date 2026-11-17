package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

class GaussianTest {
    private fun blobs(w: Int, h: Int, seed: Int): FloatArray {
        val r = Random(seed); val out = FloatArray(w * h)
        repeat(12) { val cx = r.nextInt(w); val cy = r.nextInt(h); val rad = 10 + r.nextInt(40); val v = r.nextInt(255).toFloat()
            for (y in 0 until h) for (x in 0 until w) if (Math.hypot((x - cx).toDouble(), (y - cy).toDouble()) < rad) out[y * w + x] = v }
        return out
    }

    @Test fun largeSigmaIsCloseToTheExactBlur() {
        val src = blobs(240, 240, 1)
        val fast = gaussianBlurPlane(src, 240, 240, 20f); val exact = gaussianBlurPlaneExact(src, 240, 240, 20f)
        val err = fast.indices.sumOf { Math.abs(fast[it] - exact[it]).toDouble() } / fast.size
        assertTrue(err < 3.0, "mean absolute error $err levels")
    }

    @Test fun smallSigmaStaysExact() {
        val src = blobs(80, 80, 2)
        assertArrayEquals(gaussianBlurPlaneExact(src, 80, 80, 4f), gaussianBlurPlane(src, 80, 80, 4f))
    }

    @Test fun aCloseUpHeadBlursInSeconds() {
        // one person filling the frame: head ellipse about 1500 px with sigma 175 (took minutes before)
        val src = blobs(1500, 1500, 3)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) { gaussianBlurPlane(src, 1500, 1500, 175f) }
    }

    @Test fun blurKeepsTheAverageBrightness() {
        val src = blobs(300, 300, 4)
        assertEquals(src.average(), gaussianBlurPlane(src, 300, 300, 30f).average(), 2.0)
    }
}
