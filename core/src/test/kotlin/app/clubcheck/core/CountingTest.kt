package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CountingTest {
    private fun blank(w: Int = 1280, h: Int = 720) = RgbImage(w, h, IntArray(w * h))

    /** Detector double: [whole] is returned for the full image, [perTile] for any smaller (tile) image. */
    private class Fake(val whole: List<Box>, val perTile: List<Box> = emptyList()) : PersonDetector {
        var calls = 0
        override fun detect(img: RgbImage, minConfidence: Float): List<Box> {
            calls++
            return (if (img.width == 1280 && img.height == 720) whole else perTile).filter { it.score >= minConfidence }
        }
    }
    private fun box(x: Float, score: Float) = Box(x, 100f, x + 40f, 250f, score)

    @Test fun tilesCoverImageWithOverlap() {
        val tiles = tileRects(1280, 720)
        assertEquals(9, tiles.size)
        val covered = Array(720) { BooleanArray(1280) }
        for ((x, y, w, h) in tiles.map { it.toList() }) {
            assertTrue(x >= 0 && y >= 0 && x + w <= 1280 && y + h <= 720, "tile $x,$y,$w,$h out of bounds")
            for (yy in y until y + h) for (xx in x until x + w) covered[yy][xx] = true
        }
        assertTrue(covered.all { row -> row.all { it } }, "every pixel must be inside a tile")
        val xs = tiles.map { it[0] }.distinct().sorted()
        val tw = tiles[0][2]
        assertEquals(0.2, (tw - (xs[1] - xs[0])) / tw.toDouble(), 0.02)
    }

    @Test fun nmsDropsDuplicatesKeepsHigherScore() {
        val kept = nms(listOf(Box(0f, 0f, 100f, 100f, 0.5f), Box(5f, 5f, 105f, 105f, 0.9f), Box(300f, 0f, 400f, 100f, 0.3f)))
        assertEquals(listOf(0.9f, 0.3f), kept.map { it.score })
    }

    @Test fun countPeopleUsesThreeConfidenceLevels() {
        val boxes = listOf(0.9f, 0.6f, 0.4f, 0.3f, 0.2f, 0.1f).mapIndexed { i, s -> box(100f + i * 180f, s) }
        val result = countPeople(blank(), Fake(boxes))
        assertEquals(Range(3, 5), result.range)      // 0.35 -> 3, 0.15 -> 5 (0.1 is never counted)
        assertEquals(4, result.point)                // 0.25
        assertEquals(4, result.boxesAtPoint.size)
    }

    @Test fun detectorRunsOncePerRegion() {
        val fake = Fake(emptyList())
        countPeople(blank(), fake)
        assertEquals(10, fake.calls)                 // whole image + 3x3 tiles
    }

    @Test fun tileDetectionsAreOffsetIntoImageCoordinates() {
        val tiles = tileRects(1280, 720)
        val result = countPeople(blank(), Fake(emptyList(), perTile = listOf(Box(10f, 10f, 50f, 110f, 0.5f))))
        assertEquals(9, result.point)
        assertEquals(tiles.map { it[0] + 10f }.sorted(), result.boxesAtPoint.map { it.x1 }.sorted())
    }

    @Test fun rangeIsOrderedEvenWithNoDetections() {
        val r = countPeople(blank(), Fake(emptyList()))
        assertEquals(Range(0, 0), r.range); assertEquals(0, r.point)
    }

    @Test fun mergeEventTakesMaxNotSum() {
        val a = CameraCount(Range(10, 20), 15, emptyList()); val b = CameraCount(Range(12, 18), 16, emptyList())
        val m = mergeEvent(listOf(a, b))
        assertEquals(Range(12, 20), m.range); assertEquals(16, m.point)
        assertEquals(Range(0, 0), mergeEvent(emptyList()).range)
    }

    @Test fun parseHandRangeAcceptsRangeAndSingleNumber() {
        assertEquals(Range(55, 65), parseHandRange("55-65").getOrThrow())
        assertEquals(Range(55, 65), parseHandRange(" 55 – 65 ").getOrThrow())
        assertEquals(Range(60, 60), parseHandRange("60").getOrThrow())
    }

    @Test fun parseHandRangeNormalisesReversedInput() = assertEquals(Range(55, 65), parseHandRange("65-55").getOrThrow())

    @Test fun parseHandRangeRejectsBadInput() {
        for (bad in listOf("", "   ", "abc", "-5", "5-", "1-2-3", "70000", "5.5")) assertTrue(parseHandRange(bad).isFailure, "'$bad' should fail")
    }

    @Test fun flaggedOnlyWhenRangesAreMoreThanThreeApart() {
        assertFalse(flagged(Range(10, 12), Range(15, 20)))   // gap 3
        assertTrue(flagged(Range(10, 12), Range(16, 20)))    // gap 4
        assertFalse(flagged(Range(55, 65), Range(56, 74)))   // overlap
        assertTrue(flagged(Range(40, 50), Range(10, 12)))    // either direction
    }
}
