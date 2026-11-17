package app.clubcheck.upload

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.clubcheck.core.StoredKind
import app.clubcheck.core.StoredOriginal
import app.clubcheck.core.originalsToPurge
import app.clubcheck.data.AppDb
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

fun appDb(context: Context): AppDb = Room.databaseBuilder(context.applicationContext, AppDb::class.java, "clubcheck.db").build()

/** Posts to the ClubCheck service. The base URL is configured at build time; the service is built in a later plan. */
class HttpCloudApi(private val baseUrl: String, private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()) : CloudApi {
    override suspend fun postCheckIn(payload: ByteArray, image: ByteArray): Boolean = try {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("payload", "payload.bin", payload.toRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("image", "image.jpg", image.toRequestBody("image/jpeg".toMediaType())).build()
        client.newCall(Request.Builder().url("$baseUrl/checkin").post(body).build()).execute().use { it.isSuccessful }
    } catch (e: java.io.IOException) { false }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = appDb(applicationContext).checkIns()
        val store = object : CheckInStore {
            override suspend fun queued() = dao.queued().map { QueuedCheckIn(it.id, it.eventId, it.payloadHex, it.imagePath) }
            override suspend fun markSent(id: Long) = dao.markSent(id)
        }
        val base = inputData.getString("baseUrl") ?: return Result.failure()
        UploadQueue(store, HttpCloudApi(base)) { File(it).readBytes() }.drain()
        return if (store.queued().isEmpty()) Result.success() else Result.retry()
    }
}

/** Daily: deletes original photos older than seven days (blurred exports are never touched). */
class PurgeOriginalsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dir = File(applicationContext.filesDir, "originals")
        val files = dir.listFiles().orEmpty().map { StoredOriginal(it.path, it.lastModified(), StoredKind.ORIGINAL) }
        originalsToPurge(files, System.currentTimeMillis()).forEach { File(it.path).delete() }
        return Result.success()
    }
}
