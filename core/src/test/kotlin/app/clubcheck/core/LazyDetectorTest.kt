package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LazyDetectorTest {
    private val img = RgbImage(4, 4, IntArray(16))

    @Test fun personDetectorIsNotCreatedUntilFirstUse() {
        var created = 0
        val lazy = LazyPersonDetector { created++; PersonDetector { _, _ -> listOf(Box(0f, 0f, 1f, 1f, 0.9f)) } }
        assertEquals(0, created)
        assertEquals(1, lazy.detect(img, 0.1f).size); lazy.detect(img, 0.1f)
        assertEquals(1, created)
    }

    @Test fun aMissingModelSurfacesAtFirstUseNotAtConstruction() {
        val lazy = LazyPersonDetector { throw IllegalStateException("model missing") }   // building it must not throw
        assertThrows(IllegalStateException::class.java) { lazy.detect(img, 0.1f) }
    }

    @Test fun faceFinderIsLazyToo() {
        var created = 0
        val lazy = LazyFaceFinder { created++; FaceFinder { emptyList() } }
        assertEquals(0, created); lazy.detect(img); assertEquals(1, created)
    }
}
