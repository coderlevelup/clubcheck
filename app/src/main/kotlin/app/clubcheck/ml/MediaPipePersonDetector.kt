package app.clubcheck.ml

import android.content.Context
import app.clubcheck.core.Box
import app.clubcheck.core.PersonDetector
import app.clubcheck.core.RgbImage
import app.clubcheck.export.toBitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * People counting with MediaPipe's object detector and an EfficientDet-Lite model (Apache-2.0).
 * The model file `efficientdet_lite0.tflite` must be placed in `app/src/main/assets/` (see docs/device-test.md).
 */
class MediaPipePersonDetector(context: Context, modelAsset: String = "efficientdet_lite0.tflite") : PersonDetector, AutoCloseable {
    private val detector: ObjectDetector = try {
        ObjectDetector.createFromOptions(
            context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAsset).build())
                .setRunningMode(RunningMode.IMAGE)
                .setCategoryAllowlist(listOf("person"))
                .setScoreThreshold(0.15f)
                .setMaxResults(100)
                .build()
        )
    } catch (e: Exception) {
        throw IllegalStateException("People counting model '$modelAsset' is missing or unreadable. Add it to app/src/main/assets/.", e)
    }

    override fun detect(img: RgbImage, minConfidence: Float): List<Box> {
        val bmp = img.toBitmap()
        try {
            return detector.detect(BitmapImageBuilder(bmp).build()).detections().mapNotNull { d ->
                val score = d.categories().firstOrNull()?.score() ?: return@mapNotNull null
                if (score < minConfidence) null else d.boundingBox().let { Box(it.left, it.top, it.right, it.bottom, score) }
            }
        } finally { bmp.recycle() }
    }

    override fun close() = detector.close()
}
