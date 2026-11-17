package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RetentionTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day

    @Test fun purgeSelectsOnlyOriginalsOlderThanSevenDays() {
        val keep = StoredOriginal("a.jpg", now - (6 * day + 23 * hour))
        val purge = StoredOriginal("b.jpg", now - (7 * day + hour))
        val export = StoredOriginal("c.jpg", now - 30 * day, StoredKind.EXPORT)
        assertEquals(listOf(purge), originalsToPurge(listOf(keep, purge, export), now))
    }

    @Test fun keepDaysIsConfigurable() {
        val f = StoredOriginal("a.jpg", now - 2 * day)
        assertEquals(listOf(f), originalsToPurge(listOf(f), now, keepDays = 1))
        assertEquals(emptyList<StoredOriginal>(), originalsToPurge(listOf(f), now, keepDays = 3))
    }

    @Test fun futureTimestampsAreKept() {
        assertEquals(emptyList<StoredOriginal>(), originalsToPurge(listOf(StoredOriginal("a.jpg", now + day)), now))
    }
}
