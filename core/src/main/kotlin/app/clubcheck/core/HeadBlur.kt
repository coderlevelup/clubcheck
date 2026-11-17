package app.clubcheck.core

data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val sigma: Float)

/** Head region estimated from the top of a person box (matches the spike). */
fun headEllipse(person: Box): Ellipse {
    val w = person.x2 - person.x1; val h = person.y2 - person.y1
    val d = maxOf(minOf(0.5f * w, 0.22f * h), 28f)
    return Ellipse((person.x1 + person.x2) / 2, person.y1 + 0.55f * d, 0.75f * d, 0.8f * d, maxOf(0.5f * d, 10f))
}

/** A detected face, padded generously. */
fun faceEllipse(face: Box): Ellipse {
    val w = face.x2 - face.x1; val h = face.y2 - face.y1
    val d = maxOf(w, h, 24f)
    return Ellipse((face.x1 + face.x2) / 2, (face.y1 + face.y2) / 2, 0.85f * d, 0.95f * d, maxOf(0.45f * d, 8f))
}

/** Returns a copy of [img] with a soft Gaussian-blurred ellipse over every head and face; the input is untouched. */
fun blurHeads(img: RgbImage, people: List<Box>, faces: List<Box>): RgbImage {
    val out = img.copy()
    for (e in people.map(::headEllipse) + faces.map(::faceEllipse)) blurEllipse(out, e)
    return out
}

private fun blurEllipse(img: RgbImage, e: Ellipse) {
    val margin = (3 * e.sigma).toInt() + 1
    val x0 = (e.cx - e.rx).toInt() - margin; val x1 = (e.cx + e.rx).toInt() + margin + 1
    val y0 = (e.cy - e.ry).toInt() - margin; val y1 = (e.cy + e.ry).toInt() + margin + 1
    val rx0 = x0.coerceAtLeast(0); val ry0 = y0.coerceAtLeast(0)
    val rx1 = x1.coerceAtMost(img.width); val ry1 = y1.coerceAtMost(img.height)
    val w = rx1 - rx0; val h = ry1 - ry0
    if (w <= 0 || h <= 0) return
    val planes = Array(3) { c -> FloatArray(w * h) { i -> ((img.argb[(ry0 + i / w) * img.width + rx0 + i % w] shr (16 - 8 * c)) and 0xFF).toFloat() } }
    val blurred = planes.map { gaussianBlurPlane(it, w, h, e.sigma) }
    val mask = FloatArray(w * h) { i ->
        val dx = (rx0 + i % w + 0.5f - e.cx) / e.rx; val dy = (ry0 + i / w + 0.5f - e.cy) / e.ry
        if (dx * dx + dy * dy <= 1f) 1f else 0f
    }
    val soft = gaussianBlurPlane(mask, w, h, maxOf(e.sigma * 0.25f, 2f))
    for (i in 0 until w * h) {
        val m = soft[i]; if (m <= 0f) continue
        val idx = (ry0 + i / w) * img.width + rx0 + i % w
        var px = 0xFF shl 24
        for (c in 0 until 3) {
            val v = planes[c][i] * (1 - m) + blurred[c][i] * m
            px = px or (Math.round(v).coerceIn(0, 255) shl (16 - 8 * c))
        }
        img.argb[idx] = px
    }
}
