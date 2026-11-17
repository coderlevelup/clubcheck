package app.clubcheck.ml

import app.clubcheck.core.Box
import app.clubcheck.core.FaceFinder
import app.clubcheck.core.RgbImage
import app.clubcheck.core.crop
import app.clubcheck.core.nms
import app.clubcheck.core.tileRects
import app.clubcheck.export.toBitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Face pass using ML Kit's bundled (offline) face detector, run over the whole photo and the 3x3 tiles
 * because faces in a crowd are small. ML Kit gives no per-face score, so every face returned gets blurred.
 * Blocking: call from a background thread.
 */
class MlKitFaceDetector : FaceFinder, AutoCloseable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.02f)
            .build()
    )

    override fun detect(img: RgbImage): List<Box> {
        val found = ArrayList<Box>()
        fun run(part: RgbImage, ox: Int, oy: Int) {
            val bmp = part.toBitmap()
            try {
                Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0))).forEach {
                    val r = it.boundingBox
                    found += Box((r.left + ox).toFloat(), (r.top + oy).toFloat(), (r.right + ox).toFloat(), (r.bottom + oy).toFloat(), 1f)
                }
            } finally { bmp.recycle() }
        }
        run(img, 0, 0)
        for ((x, y, w, h) in tileRects(img.width, img.height).map { it.toList() }) run(img.crop(x, y, w, h), x, y)
        return nms(found, 0.5f)
    }

    override fun close() = detector.close()
}
