package app.clubcheck.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LocationRuleTest {
    @Test fun locationDeniedYieldsNoneAndNeverAsksForAFix() = runBlocking {
        var asked = false
        val c = coarseCellFrom(permissionGranted = false, enabled = true) { asked = true; -33.92 to 18.42 }
        assertEquals(CoarseCell.NONE, c); assertFalse(asked)
    }

    @Test fun switchedOffByTheOrganiserYieldsNone() = runBlocking {
        assertEquals(CoarseCell.NONE, coarseCellFrom(permissionGranted = true, enabled = false) { -33.92 to 18.42 })
    }

    @Test fun noFixYieldsNone() = runBlocking { assertEquals(CoarseCell.NONE, coarseCellFrom(true, true) { null }) }

    @Test fun failingProviderYieldsNone() = runBlocking {
        assertEquals(CoarseCell.NONE, coarseCellFrom(true, true) { throw SecurityException("no") })
    }

    @Test fun fixIsSnappedAndTheExactPositionIsDropped() = runBlocking {
        assertEquals(CoarseCell(-3390, 1840), coarseCellFrom(true, true) { -33.9249 to 18.4241 })
    }
}
