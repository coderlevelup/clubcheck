package app.clubcheck.upload

import java.io.IOException

data class QueuedCheckIn(val id: Long, val eventId: String, val payloadHex: String, val imagePath: String)

interface CheckInStore {
    suspend fun queued(): List<QueuedCheckIn>
    suspend fun markSent(id: Long)
}

interface CloudApi {
    /** Posts one check-in; true when the cloud accepted it. The cloud treats a repeated event id as already received. */
    suspend fun postCheckIn(payload: ByteArray, image: ByteArray): Boolean
}

/** Sends queued check-ins oldest first. Stops at the first cloud failure; skips ones whose image file is gone. */
class UploadQueue(private val store: CheckInStore, private val api: CloudApi, private val readImage: (String) -> ByteArray) {
    suspend fun drain(): Int {
        var sent = 0
        for (row in store.queued()) {
            val image = try { readImage(row.imagePath) } catch (e: IOException) { continue }
            val payload = row.payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            if (!api.postCheckIn(payload, image)) return sent
            store.markSent(row.id); sent++
        }
        return sent
    }
}
