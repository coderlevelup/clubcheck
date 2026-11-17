package app.clubcheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.clubcheck.BuildConfig
import app.clubcheck.core.CheckInSession
import app.clubcheck.core.ExportOutcome
import app.clubcheck.core.LazyFaceFinder
import app.clubcheck.core.LazyPersonDetector
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val locationOn: Boolean = false,
    val locationAsked: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val ready: ExportOutcome.Ready? = null,
    /** One-shot: the Review screen goes to Share once, then clears this, so Back from Share stays on Review. */
    val showShare: Boolean = false,
)

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

class CheckInViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("clubcheck", 0)
    private val installId: ByteArray = prefs.getString("install", null)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
        ?: ByteArray(4).also { SecureRandom().nextBytes(it); prefs.edit().putString("install", it.hex()).apply() }

    // Detectors are built on first use, off the main thread, so a missing model is a message, not a launch crash.
    private val detector = LazyPersonDetector { MediaPipePersonDetector(app) }
    private val faces = LazyFaceFinder { MlKitFaceDetector() }
    private fun newSession() = CheckInSession(detector, faces, CoarseLocation(getApplication<Application>()), BitmapJpegEncoder, installId = installId)
        .also { it.setLocationEnabled(false) }   // off until the organiser allows it
    private var session = newSession()

    private val _ui = MutableStateFlow(CheckInUi())
    val ui: StateFlow<CheckInUi> = _ui.asStateFlow()

    private fun problem(e: Throwable, fallback: String) =
        if (e is OutOfMemoryError) "That photo is too large for this phone to process. Try again with fewer photos." else e.message ?: fallback

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
                _ui.update { it.copy(busy = false, message = problem(e, "That photo could not be counted.")) }
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(busy = false, message = problem(e, "")) }
            }
        }
    }

    /** Starts a fresh check-in; the loaded detectors are kept. */
    fun newCheckIn() { titleJob?.cancel(); session = newSession(); _ui.value = CheckInUi() }

    fun setHand(text: String) { session.setHand(text); _ui.update { it.copy(hand = text) } }

    private var titleJob: Job? = null

    fun setClub(text: String) {
        session.setClubUrl(text); _ui.update { it.copy(club = text, clubTitle = null) }
        titleJob?.cancel()
        val normalised = normaliseClubUrl(text).getOrNull() ?: return
        titleJob = viewModelScope.launch(Dispatchers.IO) {
            delay(700)    // wait until typing pauses; no network call for every keystroke
            val (title, summary) = PageFetcher().fetch(normalised)
            if (title != null) {
                session.setClubTitle(title, normalised)
                appDb(getApplication()).clubs().upsert(ClubEntity(normalised, title, summary))
                _ui.update { if (normaliseClubUrl(it.club).getOrNull() == normalised) it.copy(clubTitle = title) else it }
            }
        }
    }

    fun setLocationEnabled(on: Boolean) { session.setLocationEnabled(on); _ui.update { it.copy(locationOn = on) } }
    fun locationAsked() { _ui.update { it.copy(locationAsked = true) } }
    fun shareShown() { _ui.update { it.copy(showShare = false) } }

    fun export() {
        _ui.update { it.copy(busy = true, message = null, ready = null, showShare = false) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (val r = session.export(OverlayRenderer::draw)) {
                    is ExportOutcome.Invalid -> _ui.update { it.copy(busy = false, message = r.reason) }
                    is ExportOutcome.Ready -> {
                        val app = getApplication<Application>()
                        val id = r.payload.eventId.hex()
                        val file = File(app.filesDir, "exports").apply { mkdirs() }.let { File(it, "$id.jpg") }
                        file.writeBytes(r.jpeg)
                        appDb(app).checkIns().insert(CheckInEntity(eventId = id, payloadHex = r.payload.encode().hex(), imagePath = file.path))
                        if (BuildConfig.CLOUD_URL.isNotEmpty()) {
                            WorkManager.getInstance(app).enqueueUniqueWork(
                                "upload", ExistingWorkPolicy.APPEND_OR_REPLACE,
                                OneTimeWorkRequestBuilder<UploadWorker>().setInputData(workDataOf("baseUrl" to BuildConfig.CLOUD_URL))
                                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
                            )
                        }
                        _ui.update { it.copy(busy = false, ready = r, showShare = true) }
                    }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = problem(e, "Could not make the image. Nothing was shared.")) }
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(busy = false, message = problem(e, "")) }
            }
        }
    }
}
