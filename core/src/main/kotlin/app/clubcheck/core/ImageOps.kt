package app.clubcheck.core

/** Applies an EXIF orientation value (1-8) so the pixels are upright; anything else is treated as 1. */
fun applyOrientation(img: RgbImage, orientation: Int): RgbImage {
    if (orientation !in 2..8) return img.copy()
    val w = img.width; val h = img.height
    val swap = orientation in 5..8
    val ow = if (swap) h else w; val oh = if (swap) w else h
    val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val (nx, ny) = when (orientation) {
            2 -> (w - 1 - x) to y
            3 -> (w - 1 - x) to (h - 1 - y)
            4 -> x to (h - 1 - y)
            5 -> y to x
            6 -> (h - 1 - y) to x
            7 -> (h - 1 - y) to (w - 1 - x)
            else -> y to (w - 1 - x)
        }
        out[ny * ow + nx] = img.argb[y * w + x]
    }
    return RgbImage(ow, oh, out)
}

/** Scales to fit inside 1280x720 (area-averaged), centred on black. Nothing is cropped away. */
fun fitToExport(img: RgbImage): RgbImage {
    val s = minOf(EXPORT_WIDTH.toDouble() / img.width, EXPORT_HEIGHT.toDouble() / img.height)
    val tw = Math.round(img.width * s).toInt().coerceIn(1, EXPORT_WIDTH); val th = Math.round(img.height * s).toInt().coerceIn(1, EXPORT_HEIGHT)
    val out = IntArray(EXPORT_WIDTH * EXPORT_HEIGHT) { 0xFF000000.toInt() }
    val ox = (EXPORT_WIDTH - tw) / 2; val oy = (EXPORT_HEIGHT - th) / 2
    for (dy in 0 until th) {
        val sy0 = (dy / s).toInt().coerceAtMost(img.height - 1); val sy1 = (((dy + 1) / s).toInt()).coerceIn(sy0 + 1, img.height)
        for (dx in 0 until tw) {
            val sx0 = (dx / s).toInt().coerceAtMost(img.width - 1); val sx1 = (((dx + 1) / s).toInt()).coerceIn(sx0 + 1, img.width)
            var r = 0L; var g = 0L; var b = 0L
            for (sy in sy0 until sy1) for (sx in sx0 until sx1) { val p = img.argb[sy * img.width + sx]; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
            val n = ((sy1 - sy0) * (sx1 - sx0)).toLong()
            out[(oy + dy) * EXPORT_WIDTH + ox + dx] = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
    }
    return RgbImage(EXPORT_WIDTH, EXPORT_HEIGHT, out)
}
