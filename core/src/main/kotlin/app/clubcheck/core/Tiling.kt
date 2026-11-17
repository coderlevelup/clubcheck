package app.clubcheck.core

/** [n] x [n] tiles, each overlapping its neighbour by about [overlap] of a tile; returns `[x, y, w, h]` per tile. */
fun tileRects(w: Int, h: Int, n: Int = 3, overlap: Float = 0.2f): List<IntArray> {
    val span = n - (n - 1) * overlap
    val tw = (w / span).toInt(); val th = (h / span).toInt()
    fun start(i: Int, full: Int, size: Int) = if (i == n - 1) full - size else Math.round(i * size * (1 - overlap))
    return buildList { for (j in 0 until n) for (i in 0 until n) add(intArrayOf(start(i, w, tw), start(j, h, th), tw, th)) }
}

fun RgbImage.crop(x: Int, y: Int, w: Int, h: Int): RgbImage {
    val out = IntArray(w * h)
    for (row in 0 until h) System.arraycopy(argb, (y + row) * width + x, out, row * w, w)
    return RgbImage(w, h, out)
}
