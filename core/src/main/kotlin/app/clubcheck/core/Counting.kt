package app.clubcheck.core

data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val score: Float)

fun interface PersonDetector {
    /** Boxes in the coordinates of [img], each with score at least [minConfidence]. */
    fun detect(img: RgbImage, minConfidence: Float): List<Box>
}

data class CameraCount(val range: Range, val point: Int, val boxesAtPoint: List<Box>)

private fun area(b: Box) = (b.x2 - b.x1) * (b.y2 - b.y1)

private fun intersection(a: Box, b: Box): Float {
    val iw = minOf(a.x2, b.x2) - maxOf(a.x1, b.x1); val ih = minOf(a.y2, b.y2) - maxOf(a.y1, b.y1)
    return if (iw <= 0f || ih <= 0f) 0f else iw * ih
}

/**
 * Greedy non-maximum suppression. A box is dropped when a higher-scoring kept box overlaps it by more than
 * [iou], or when at least [containment] of its own area lies inside that box. The second rule merges the
 * partial boxes that tile seams cut a person into (their IoU with the whole body is low). Two people standing
 * side by side, or one partly behind another (under [containment] of the smaller box covered), stay separate.
 */
fun nms(boxes: List<Box>, iou: Float = 0.5f, containment: Float = 0.6f): List<Box> {
    val kept = ArrayList<Box>()
    for (b in boxes.sortedByDescending { it.score }) {
        val duplicate = kept.any { k ->
            val inter = intersection(k, b)
            inter > 0f && (inter / (area(k) + area(b) - inter) > iou || inter / area(b) >= containment)
        }
        if (!duplicate) kept += b
    }
    return kept
}

private const val LOW_CONF = 0.35f
private const val POINT_CONF = 0.25f
private const val HIGH_CONF = 0.15f

/**
 * Camera count as a range: whole image plus 3x3 tiles, merged with NMS, counted at three fixed confidence
 * levels (0.35 low, 0.25 point, 0.15 high). The detector runs once per region at the lowest level and the
 * results are filtered per level, which gives the same boxes as three separate runs at a third of the cost.
 */
fun countPeople(img: RgbImage, detector: PersonDetector): CameraCount {
    val all = ArrayList<Box>()
    all += detector.detect(img, HIGH_CONF)
    for ((x, y, w, h) in tileRects(img.width, img.height).map { it.toList() }) {
        detector.detect(img.crop(x, y, w, h), HIGH_CONF).forEach { all += it.copy(x1 = it.x1 + x, x2 = it.x2 + x, y1 = it.y1 + y, y2 = it.y2 + y) }
    }
    fun at(level: Float) = nms(all.filter { it.score >= level })
    val low = at(LOW_CONF); val point = at(POINT_CONF); val high = at(HIGH_CONF)
    val lo = minOf(low.size, point.size, high.size); val hi = maxOf(low.size, point.size, high.size)
    return CameraCount(Range(lo, hi), point.size.coerceIn(lo, hi), point)
}

/** One event can have several photos; the event count is the largest per field, never the sum. */
fun mergeEvent(photos: List<CameraCount>): CameraCount {
    if (photos.isEmpty()) return CameraCount(Range(0, 0), 0, emptyList())
    return CameraCount(
        Range(photos.maxOf { it.range.lo }, photos.maxOf { it.range.hi }),
        photos.maxOf { it.point },
        photos.maxBy { it.point }.boxesAtPoint,
    )
}

private val handPattern = Regex("""^(\d{1,5})(?:\s*[-–—]\s*(\d{1,5}))?$""")

/** `"55-65"`, `"65-55"` (normalised), or a single number `"60"` (a range of one). Blank or malformed fails. */
fun parseHandRange(text: String): Result<Range> {
    val m = handPattern.matchEntire(text.trim())
        ?: return Result.failure(IllegalArgumentException("Enter a number or a range like 55-65"))
    val a = m.groupValues[1].toInt()
    val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toInt()
    if (a > 65535 || b > 65535) return Result.failure(IllegalArgumentException("That number is too large"))
    return Result.success(Range(minOf(a, b), maxOf(a, b)))
}

/** True only when the hand range and the camera range are more than 3 people apart. */
fun flagged(hand: Range, camera: Range): Boolean {
    val gap = maxOf(camera.lo - hand.hi, hand.lo - camera.hi, 0)
    return gap > 3
}
