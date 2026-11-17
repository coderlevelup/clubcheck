package app.clubcheck.core

/** Builds the detector on first use, so a missing model shows up as a message when counting, not as a crash at launch. */
class LazyPersonDetector(private val factory: () -> PersonDetector) : PersonDetector {
    private val real by lazy { factory() }
    override fun detect(img: RgbImage, minConfidence: Float) = real.detect(img, minConfidence)
}

class LazyFaceFinder(private val factory: () -> FaceFinder) : FaceFinder {
    private val real by lazy { factory() }
    override fun detect(img: RgbImage) = real.detect(img)
}
