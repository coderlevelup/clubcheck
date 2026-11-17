package app.clubcheck.upload

import java.io.IOException

data class QueuedCheckIn(val id: Long, val eventId: String, val payloadHex: String, val imagePath: String)

interface CheckInStore {
    suspend fun queued(): List<QueuedCheckIn>
    suspend fun markSent(id: Long)
    /** Gives up on a row for good (its image is gone or the service refused it) so it no longer blocks the queue. */
    suspend fun markFailed(id: Long)
}

enum class UploadResult { OK, RETRY, REJECTED }

interface CloudApi {
    /** The cloud treats a repeated event id as already received, so a retry after a lost reply is safe. */
    suspend fun postCheckIn(payload: ByteArray, image: ByteArray): UploadResult
}

/**
 * Sends queued check-ins oldest first. Stops at the first retryable failure (probably offline); a row whose
 * image file is gone, or that the service refuses outright, is marked failed and the rest carry on.
 */
class UploadQueue(private val store: CheckInStore, private val api: CloudApi, private val readImage: (String) -> ByteArray) {
    suspend fun drain(): Int {
        var sent = 0
        for (row in store.queued()) {
            val image = try { readImage(row.imagePath) } catch (e: IOException) { store.markFailed(row.id); continue }
            val payload = row.payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            when (api.postCheckIn(payload, image)) {
                UploadResult.OK -> { store.markSent(row.id); sent++ }
                UploadResult.REJECTED -> store.markFailed(row.id)
                UploadResult.RETRY -> return sent
            }
        }
        return sent
    }
}
