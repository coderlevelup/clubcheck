package app.clubcheck.core

import java.nio.ByteBuffer

const val MAX_URL_BYTES = 65

data class Range(val lo: Int, val hi: Int)

/** A location rounded to a coarse grid, in hundredths of a degree. [NONE] means no location. */
data class CoarseCell(val latHundredths: Int, val lonHundredths: Int) {
    companion object { val NONE = CoarseCell(Short.MIN_VALUE.toInt(), Short.MIN_VALUE.toInt()) }
}

class Payload(
    val eventId: ByteArray,
    val utcSeconds: Long,
    val tz15: Int,
    val cell: CoarseCell,
    val hand: Range,
    val camera: Range,
    val cameraPoint: Int,
    val photos: Int,
    val installId: ByteArray,
    val url: String,
) {
    init {
        require(eventId.size == 6) { "eventId must be 6 bytes" }
        require(installId.size == 4) { "installId must be 4 bytes" }
        require(url.toByteArray(Charsets.UTF_8).size <= MAX_URL_BYTES) { "club URL is longer than $MAX_URL_BYTES bytes" }
    }

    fun copy(
        eventId: ByteArray = this.eventId, utcSeconds: Long = this.utcSeconds, tz15: Int = this.tz15,
        cell: CoarseCell = this.cell, hand: Range = this.hand, camera: Range = this.camera,
        cameraPoint: Int = this.cameraPoint, photos: Int = this.photos, installId: ByteArray = this.installId,
        url: String = this.url,
    ) = Payload(eventId, utcSeconds, tz15, cell, hand, camera, cameraPoint, photos, installId, url)

    fun encode(): ByteArray {
        val u = url.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(FIXED_BYTES + u.size)
        buf.put(1).put(eventId).putInt(utcSeconds.toInt()).put(tz15.toByte())
            .putShort(cell.latHundredths.toShort()).putShort(cell.lonHundredths.toShort())
            .putShort(hand.lo.toShort()).putShort(hand.hi.toShort())
            .putShort(camera.lo.toShort()).putShort(camera.hi.toShort()).putShort(cameraPoint.toShort())
            .put(photos.toByte()).put(installId).put(u.size.toByte()).put(u)
        val body = buf.array()
        return body + byteArrayOf((crc16(body) shr 8).toByte(), crc16(body).toByte())
    }

    override fun equals(other: Any?) = other is Payload && eventId.contentEquals(other.eventId) &&
        utcSeconds == other.utcSeconds && tz15 == other.tz15 && cell == other.cell && hand == other.hand &&
        camera == other.camera && cameraPoint == other.cameraPoint && photos == other.photos &&
        installId.contentEquals(other.installId) && url == other.url

    override fun hashCode() = eventId.contentHashCode() * 31 + utcSeconds.hashCode()
    override fun toString() = "Payload(event=${eventId.joinToString("") { "%02x".format(it) }}, url=$url, hand=$hand, camera=$camera)"

    companion object {
        /** version, event id, utc, tz, lat, lon, 5 x u16, photos, install id, url length. */
        const val FIXED_BYTES = 1 + 6 + 4 + 1 + 2 + 2 + 2 * 5 + 1 + 4 + 1

        fun decode(bytes: ByteArray): Payload? {
            if (bytes.size < FIXED_BYTES + 2) return null
            val body = bytes.copyOf(bytes.size - 2)
            val crc = ((bytes[bytes.size - 2].toInt() and 0xFF) shl 8) or (bytes[bytes.size - 1].toInt() and 0xFF)
            if (crc16(body) != crc) return null
            val buf = ByteBuffer.wrap(body)
            if (buf.get().toInt() != 1) return null
            val event = ByteArray(6).also { buf.get(it) }
            val ts = buf.int.toLong() and 0xFFFFFFFFL
            val tz = buf.get().toInt()
            val lat = buf.short.toInt(); val lon = buf.short.toInt()
            val handLo = buf.short.toInt() and 0xFFFF; val handHi = buf.short.toInt() and 0xFFFF
            val camLo = buf.short.toInt() and 0xFFFF; val camHi = buf.short.toInt() and 0xFFFF
            val point = buf.short.toInt() and 0xFFFF
            val photos = buf.get().toInt() and 0xFF
            val inst = ByteArray(4).also { buf.get(it) }
            val ulen = buf.get().toInt() and 0xFF
            if (ulen > MAX_URL_BYTES || buf.remaining() != ulen) return null
            val url = ByteArray(ulen).also { buf.get(it) }.toString(Charsets.UTF_8)
            return Payload(event, ts, tz, CoarseCell(lat, lon), Range(handLo, handHi), Range(camLo, camHi), point, photos, inst, url)
        }
    }
}
