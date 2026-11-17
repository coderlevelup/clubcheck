package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClubUrlTest {
    @Test fun normaliseHandlesCaseSchemeSlash() {
        assertEquals("rsvp.withgoogle.com/events/x", normaliseClubUrl("HTTPS://RSVP.withgoogle.com/events/x/").getOrThrow())
        assertEquals("rsvp.withgoogle.com/events/x", normaliseClubUrl("  http://rsvp.withgoogle.com/events/x  ").getOrThrow())
        assertEquals("club.example", normaliseClubUrl("club.example/").getOrThrow())
    }

    @Test fun normaliseKeepsPathCase() = assertEquals("club.example/Events/Chess", normaliseClubUrl("https://Club.Example/Events/Chess").getOrThrow())

    @Test fun normaliseAcceptsExactly65Bytes() {
        val url = "club.example/" + "a".repeat(65 - "club.example/".length)
        assertEquals(65, normaliseClubUrl(url).getOrThrow().length)
    }

    @Test fun normaliseRejectsOver65Bytes() {
        val r = normaliseClubUrl("club.example/" + "a".repeat(66 - "club.example/".length))
        assertTrue(r.isFailure); assertTrue(r.exceptionOrNull()!!.message!!.contains("65"))
    }

    @Test fun normaliseRejectsEmptyOrBlankOrSpaces() {
        for (bad in listOf("", "   ", "https://", "club example/x")) assertTrue(normaliseClubUrl(bad).isFailure, "'$bad'")
    }

    @Test fun normaliseRejectsOtherSchemes() {
        for (bad in listOf("ftp://club.example", "mailto:a@b.c", "javascript:alert(1)")) assertTrue(normaliseClubUrl(bad).isFailure, bad)
    }

    @Test fun pageMetaPrefersOgTags() {
        val html = """<html><head><title>Plain title</title>
            <meta name="description" content="Plain desc">
            <meta property="og:title" content="Android Developer Day Cape Town">
            <meta property="og:description" content="Talks &amp; workshops"></head></html>"""
        assertEquals("Android Developer Day Cape Town" to "Talks & workshops", parsePageMeta(html))
    }

    @Test fun pageMetaFallsBackToTitleAndDescription() {
        val html = "<html><head><TITLE>\n  Chess   Club \n</TITLE><meta name='description' content='We play on Tuesdays'></head>"
        assertEquals("Chess Club" to "We play on Tuesdays", parsePageMeta(html))
    }

    @Test fun pageMetaHandlesContentBeforeProperty() {
        assertEquals("T" to null, parsePageMeta("""<meta content="T" property="og:title">"""))
    }

    @Test fun pageMetaReturnsNullsWhenAbsent() = assertEquals(null to null, parsePageMeta("<html><body>hi</body></html>"))
}
