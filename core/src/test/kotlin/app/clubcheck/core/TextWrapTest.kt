package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextWrapTest {
    @Test fun shortTextIsUnchanged() = assertEquals(listOf("CHESS CLUB"), wrapLine("CHESS CLUB", 40))
    @Test fun emptyTextGivesOneEmptyLine() = assertEquals(listOf(""), wrapLine("", 10))

    @Test fun wrapsAtSpaces() = assertEquals(listOf("ANDROID DEVELOPER", "DAY CAPE TOWN"), wrapLine("ANDROID DEVELOPER DAY CAPE TOWN", 20))

    @Test fun aLongLinkBreaksAfterSlashesAndDashes() {
        val lines = wrapLine("rsvp.withgoogle.com/events/android-developer-day-capetown/session-2", 30)
        assertTrue(lines.size >= 3); assertTrue(lines.all { it.length <= 30 }, lines.toString())
        assertEquals("rsvp.withgoogle.com/events/android-developer-day-capetown/session-2", lines.joinToString(""))
        assertTrue(lines.dropLast(1).all { it.last() in "/-." }, "breaks only at natural points: $lines")
    }

    @Test fun anUnbreakableRunIsSplitHard() {
        val lines = wrapLine("a".repeat(45), 20)
        assertEquals(listOf(20, 20, 5), lines.map { it.length }); assertEquals("a".repeat(45), lines.joinToString(""))
    }

    @Test fun neverExceedsTheWidthAndLosesNothing() {
        val text = "Cape Town Observatory ORT SA Cape Science Centre https://zen.coderdojo.com/dojos/za/western-cape/cape-town"
        for (w in 8..60) {
            val lines = wrapLine(text, w)
            assertTrue(lines.all { it.length <= w }, "width $w: $lines")
            assertEquals(text.replace(" ", ""), lines.joinToString("").replace(" ", ""), "width $w")
        }
    }
}
