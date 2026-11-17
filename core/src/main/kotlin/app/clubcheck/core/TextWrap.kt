package app.clubcheck.core

private const val BREAK_AFTER = "/-."

/**
 * Splits [text] into lines of at most [maxChars] characters: at the latest space that fits, or just after a
 * '/', '-' or '.' (so a long web link breaks at natural points), or hard in the middle of an unbreakable run.
 */
fun wrapLine(text: String, maxChars: Int): List<String> {
    require(maxChars >= 1)
    if (text.length <= maxChars) return listOf(text)
    val lines = ArrayList<String>()
    var start = 0
    while (text.length - start > maxChars) {
        var cut = -1
        for (i in start + 1..start + maxChars) {
            if (text[i - 1] in BREAK_AFTER) cut = i
            if (text[i] == ' ') cut = i
        }
        if (cut <= start) cut = start + maxChars
        lines += text.substring(start, cut).trimEnd()
        start = cut
        while (start < text.length && text[start] == ' ') start++
    }
    if (start < text.length) lines += text.substring(start)
    return lines
}
