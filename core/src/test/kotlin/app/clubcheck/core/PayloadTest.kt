package app.clubcheck.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayloadTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun vectors(): List<JSONObject> {
        val text = javaClass.getResourceAsStream("/vectors.json")!!.reader().readText()
        val arr = JSONArray(text)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
    private fun payloadOf(v: JSONObject) = Payload(
        eventId = hex(v.getString("eventId")),
        utcSeconds = v.getLong("utcSeconds"),
        tz15 = v.getInt("tz15"),
        cell = CoarseCell(v.getInt("latHundredths"), v.getInt("lonHundredths")),
        hand = Range(v.getInt("handLo"), v.getInt("handHi")),
        camera = Range(v.getInt("camLo"), v.getInt("camHi")),
        cameraPoint = v.getInt("camPoint"),
        photos = v.getInt("photos"),
        installId = hex(v.getString("installId")),
        url = v.getString("url"),
    )

    @Test fun encodeMatchesPythonVectors() {
        for (v in vectors()) {
            val p = payloadOf(v)
            assertArrayEquals(hex(v.getString("payloadHex")), p.encode(), "payload ${v.getString("name")}")
            assertArrayEquals(hex(v.getString("codedHex")), ReedSolomon.encode(p.encode()), "coded ${v.getString("name")}")
        }
    }

    @Test fun decodeRoundTrips() {
        for (v in vectors()) {
            val p = payloadOf(v)
            assertEquals(p, Payload.decode(p.encode()), v.getString("name"))
        }
    }

    @Test fun noLocationUsesSentinel() {
        val v = vectors().first { it.getString("name") == "no-location" }
        val p = payloadOf(v).copy(cell = CoarseCell.NONE)
        val bytes = p.encode()
        assertArrayEquals(hex(v.getString("payloadHex")), bytes)
        assertEquals(Short.MIN_VALUE.toInt(), CoarseCell.NONE.latHundredths)
        assertEquals(Short.MIN_VALUE.toInt(), CoarseCell.NONE.lonHundredths)
        assertEquals(CoarseCell.NONE, Payload.decode(bytes)!!.cell)
    }

    @Test fun corruptedCrcReturnsNull() {
        val bytes = payloadOf(vectors().first()).encode()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x55).toByte()
        assertNull(Payload.decode(bytes))
    }

    @Test fun rsCorrectsTwentyByteErrors() {
        val original = payloadOf(vectors().first()).encode()
        val coded = ReedSolomon.encode(original)
        for (i in 0 until 20) coded[i * 6] = (coded[i * 6].toInt() xor 0xA5).toByte()
        assertArrayEquals(original, ReedSolomon.decode(coded))
    }

    @Test fun rsFailsBeyondCapacity() {
        val original = payloadOf(vectors().first()).encode()
        val coded = ReedSolomon.encode(original)
        for (i in 0 until 25) coded[i * 5] = (coded[i * 5].toInt() xor 0xA5).toByte()
        val decoded = ReedSolomon.decode(coded)
        assertTrue(decoded == null || Payload.decode(decoded) == null, "25 errors must not yield a valid payload")
    }

    @Test fun urlOverMaxIsRejected() {
        val v = vectors().first()
        assertThrows(IllegalArgumentException::class.java) { payloadOf(v).copy(url = "a".repeat(MAX_URL_BYTES + 1)) }
        assertEquals(120, MAX_URL_BYTES)
    }
}
