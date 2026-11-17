package app.clubcheck.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlaceTest {
    @Test fun snapsCapeTownToExpectedCell() = assertEquals(CoarseCell(-3390, 1840), snapToCell(-33.9249, 18.4241))

    @Test fun snapNeverReturnsFinerThanGrid() {
        val rnd = Random(42)
        repeat(1000) {
            val c = snapToCell(rnd.nextDouble(-90.0, 90.0), rnd.nextDouble(-180.0, 180.0))
            assertEquals(0, Math.floorMod(c.latHundredths, 5)); assertEquals(0, Math.floorMod(c.lonHundredths, 5))
        }
    }

    @Test fun snapStaysWithinHalfAGridCell() {
        val rnd = Random(7)
        repeat(1000) {
            val lat = rnd.nextDouble(-89.0, 89.0); val c = snapToCell(lat, 0.0)
            assertTrue(Math.abs(c.latHundredths / 100.0 - lat) <= 0.0251, "lat $lat snapped to ${c.latHundredths}")
        }
    }

    @Test fun tz15ForSast() = assertEquals(8, tz15(7200))
    @Test fun tz15ForIndia() = assertEquals(22, tz15(19800))
    @Test fun tz15ForNewfoundland() = assertEquals(-10, tz15(-9000))
    @Test fun labelNoneIsNull() = assertNull(cellLabel(CoarseCell.NONE))
    @Test fun labelHandlesHemispheres() {
        assertEquals("33.90S 18.40E", cellLabel(CoarseCell(-3390, 1840)))
        assertEquals("51.20N 122.00W", cellLabel(CoarseCell(5120, -12200)))
    }
}
