package app.clubcheck.core

import java.util.Locale

/** Snaps a position to the nearest [grid] degree cell (default 0.05, about 5 km). Only the cell is ever kept. */
fun snapToCell(lat: Double, lon: Double, grid: Double = 0.05): CoarseCell {
    val step = Math.round(grid * 100).toInt()
    require(step > 0 && Math.abs(grid * 100 - step) < 1e-9) { "grid must be a whole number of hundredths of a degree" }
    fun snap(v: Double) = (Math.round(v * 100.0 / step) * step).toInt()
    return CoarseCell(snap(lat), snap(lon))
}

/** Timezone offset in 15-minute units. */
fun tz15(offsetSeconds: Int): Int = offsetSeconds / 900

/** `"33.90S 18.40E"`, or null when there is no location. */
fun cellLabel(c: CoarseCell): String? {
    if (c == CoarseCell.NONE) return null
    fun part(v: Int, pos: Char, neg: Char) = String.format(Locale.ROOT, "%.2f%c", Math.abs(v) / 100.0, if (v < 0) neg else pos)
    return part(c.latHundredths, 'N', 'S') + " " + part(c.lonHundredths, 'E', 'W')
}
