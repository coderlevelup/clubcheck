package app.clubcheck.core

/**
 * Club identity: the page address without scheme or trailing slash, host in lower case, path case kept.
 * Fails for empty input, spaces, schemes other than http(s), and anything over [MAX_URL_BYTES] bytes.
 */
fun normaliseClubUrl(input: String): Result<String> {
    var s = input.trim()
    val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):").find(s)?.groupValues?.get(1)
    if (scheme != null && !s.startsWith("$scheme://", ignoreCase = true) || (scheme != null && scheme.lowercase() !in setOf("http", "https"))) {
        return Result.failure(IllegalArgumentException("Use a web address starting with http or https"))
    }
    s = s.replace(Regex("^(?i)https?://"), "").trimEnd('/')
    if (s.isEmpty() || s.any { it.isWhitespace() }) return Result.failure(IllegalArgumentException("Enter the club's web address, for example club.example/chess"))
    val slash = s.indexOf('/')
    s = if (slash < 0) s.lowercase() else s.substring(0, slash).lowercase() + s.substring(slash)
    if (s.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES) {
        return Result.failure(IllegalArgumentException("That address is too long ($MAX_URL_BYTES characters at most). Use a shorter link."))
    }
    return Result.success(s)
}

private fun clean(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim().ifEmpty { null }

private fun metaContent(html: String, key: String): String? {
    val tag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    for (m in tag.findAll(html)) {
        val t = m.value
        val named = Regex("""(?:property|name)\s*=\s*["']${Regex.escape(key)}["']""", RegexOption.IGNORE_CASE).containsMatchIn(t)
        if (!named) continue
        Regex("""content\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(t)?.let { c ->
            return clean(c.groupValues[1].ifEmpty { c.groupValues[2] })
        }
    }
    return null
}

/** (title, summary): `og:title` then `<title>`; `og:description` then the description meta tag. */
fun parsePageMeta(html: String): Pair<String?, String?> {
    val title = metaContent(html, "og:title")
        ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.let { clean(it.groupValues[1]) }
    val summary = metaContent(html, "og:description") ?: metaContent(html, "description")
    return title to summary
}
