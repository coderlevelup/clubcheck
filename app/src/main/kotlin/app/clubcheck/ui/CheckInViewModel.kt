package app.clubcheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.clubcheck.BuildConfig
import app.clubcheck.core.CheckInSession
import app.clubcheck.core.ExportOutcome
import app.clubcheck.core.normaliseClubUrl
import app.clubcheck.data.CheckInEntity
import app.clubcheck.data.ClubEntity
import app.clubcheck.data.PageFetcher
import app.clubcheck.export.BitmapJpegEncoder
import app.clubcheck.export.OverlayRenderer
import app.clubcheck.export.decodeUpright
import app.clubcheck.location.CoarseLocation
import app.clubcheck.ml.MediaPipePersonDetector
import app.clubcheck.ml.MlKitFaceDetector
import app.clubcheck.upload.UploadWorker
import app.clubcheck.upload.appDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom

data class CheckInUi(
    val photos: Int = 0,
    val cameraText: String = "No photos yet",
    val hand: String = "",
    val club: String = "",
    val clubTitle: String? = null,
    val locationOn: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val ready: ExportOutcome.Ready? = null,
)

class CheckInViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("clubcheck", 0)
    private val installId: ByteArray = prefs.getString("install", null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
        ?: ByteArray(4).also { SecureRandom().nextBytes(it); prefs.edit().putString("install", it.joinToString("") { b -> "%02x".format(b) }).apply() }

    private val detector by lazy { MediaPipePersonDetector(app) }
    private val faces by lazy { MlKitFaceDetector() }
    private fun newSession() = CheckInSession(detector, faces, CoarseLocation(app), BitmapJpegEncoder, installId = installId)
    private var session = newSession()
    private val _ui = MutableStateFlow(CheckInUi())
    val ui: StateFlow<CheckInUi> = _ui.asStateFlow()

    /** [jpeg] is the camera file. It is kept on the phone for seven days, counted now, and never uploaded. */
    fun onPhoto(jpeg: ByteArray) {
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                File(getApplication<Application>().filesDir, "originals").apply { mkdirs() }.let { File(it, "${System.currentTimeMillis()}.jpg").writeBytes(jpeg) }
                session.addPhoto(decodeUpright(jpeg))
                val c = session.camera()
                _ui.update { it.copy(busy = false, photos = session.photoCount, cameraText = "${c.range.lo}-${c.range.hi} (best guess ${c.point})") }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = e.message ?: "That photo could not be counted.") }
            }
        }
    }

    /** Starts a fresh check-in; the loaded detectors are kept. */
    fun newCheckIn() { session = newSession(); _ui.value = CheckInUi() }

    fun setHand(text: String) { session.setHand(text); _ui.update { it.copy(hand = text) } }

    fun setClub(text: String) {
        session.setClubUrl(text); _ui.update { it.copy(club = text, clubTitle = null) }
        val normalised = normaliseClubUrl(text).getOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val (title, summary) = PageFetcher().fetch(normalised)
            if (title != null) {
                session.setClubTitle(title)
                appDb(getApplication()).clubs().upsert(ClubEntity(normalised, title, summary))
                _ui.update { if (it.club == text) it.copy(clubTitle = title) else it }
            }
        }
    }

    fun setLocationEnabled(on: Boolean) { session.setLocationEnabled(on); _ui.update { it.copy(locationOn = on) } }

    fun export() {
        _ui.update { it.copy(busy = true, message = null, ready = null) }
        viewModelScope.launch(Dispatchers.Default) {
            when (val r = session.export(OverlayRenderer::draw)) {
                is ExportOutcome.Invalid -> _ui.update { it.copy(busy = false, message = r.reason) }
                is ExportOutcome.Ready -> {
                    val app = getApplication<Application>()
                    val file = File(app.filesDir, "exports").apply { mkdirs() }.let { File(it, "${r.payload.eventId.joinToString("") { b -> "%02x".format(b) }}.jpg") }
                    file.writeBytes(r.jpeg)
                    appDb(app).checkIns().insert(CheckInEntity(eventId = r.payload.eventId.joinToString("") { "%02x".format(it) }, payloadHex = r.payload.encode().joinToString("") { "%02x".format(it) }, imagePath = file.path))
                    if (BuildConfig.CLOUD_URL.isNotEmpty()) {
                        WorkManager.getInstance(app).enqueue(
                            OneTimeWorkRequestBuilder<UploadWorker>().setInputData(workDataOf("baseUrl" to BuildConfig.CLOUD_URL))
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
                        )
                    }
                    _ui.update { it.copy(busy = false, ready = r) }
                }
            }
        }
    }
}
