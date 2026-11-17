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

@Volatile private var database: AppDb? = null

/** One shared database for the whole process (Room instances are expensive and must not be duplicated). */
fun appDb(context: Context): AppDb = database ?: synchronized(AppDb::class.java) {
    database ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "clubcheck.db").build().also { database = it }
}

/** Posts to the ClubCheck service. The base URL is configured at build time; the service is built in a later plan. */
class HttpCloudApi(private val baseUrl: String, private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()) : CloudApi {
    override suspend fun postCheckIn(payload: ByteArray, image: ByteArray): UploadResult = try {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("payload", "payload.bin", payload.toRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("image", "image.jpg", image.toRequestBody("image/jpeg".toMediaType())).build()
        client.newCall(Request.Builder().url("$baseUrl/checkin").post(body).build()).execute().use {
            when {
                it.isSuccessful -> UploadResult.OK
                it.code in 400..499 && it.code != 408 && it.code != 429 -> UploadResult.REJECTED
                else -> UploadResult.RETRY
            }
        }
    } catch (e: java.io.IOException) { UploadResult.RETRY } catch (e: IllegalArgumentException) { UploadResult.REJECTED }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = appDb(applicationContext).checkIns()
        val store = object : CheckInStore {
            override suspend fun queued() = dao.queued().map { QueuedCheckIn(it.id, it.eventId, it.payloadHex, it.imagePath) }
            override suspend fun markSent(id: Long) = dao.markSent(id)
            override suspend fun markFailed(id: Long) = dao.markFailed(id)
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
