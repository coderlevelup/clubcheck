# ClubCheck Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app (Kotlin, Jetpack Compose) that takes event photos, counts people on-device as a range, blurs heads and faces, and exports one shareable JPEG carrying its own data (visible overlay, data strip, hidden watermark), with an offline-first upload queue.

**Architecture:** Two Gradle modules. `:core` is plain Kotlin/JVM (payload, Reed-Solomon, strip and watermark codecs, tiling and counting rules, blur geometry, coarse cell) so everything risky is unit-tested here without a phone. `:app` is the Android shell (CameraX, MediaPipe, ML Kit, Room, WorkManager, Compose) and stays thin. The Python spike in `spikes/2026-11-17-event-photo/` is the reference implementation; `:core` must agree with it on shared test vectors.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Jetpack Compose, CameraX, MediaPipe Tasks (object detection), ML Kit face detection, Room, WorkManager, ZXing core (Reed-Solomon only), JUnit 5, `javax.imageio` for JPEG in tests.

**Spec:** `docs/superpowers/specs/2026-11-17-club-checkin-design.md`. Scope: the Android app only. The Cloudflare service and dashboard are a separate plan, written after this one; this plan ends with an upload client against an interface.

## Global Constraints

- Android only; Kotlin; Jetpack Compose UI ("Compose first"). minSdk 26 and the latest stable compileSdk, library versions checked against current guidance (my call).
- Package `app.clubcheck`; modules `:core` (JVM, no Android imports) and `:app`.
- Original photos never leave the device (auto-delete after 7 days by default). Only the blurred export is shared or uploaded.
- Location: approximate only (`ACCESS_COARSE_LOCATION`), snapped on-device to a 0.05 degree grid; no precise coordinate is ever stored, embedded or sent. Opt-in per check-in, editable, optional.
- Time: phone clock as UTC seconds plus local timezone offset in 15-minute units.
- Counts are ranges. Hand range typed by the organiser. Camera range = 3x3 tiled count (plus whole image, tiles overlap 20%) at confidence 0.35 (low), 0.25 (point), 0.15 (high), NMS IoU 0.5; fixed, never tuned per photo. Flag only when the ranges are more than 3 people apart.
- Export: 1280x720 JPEG (quality 92), no EXIF or other metadata, strip + overlay + watermark + repo footer `github.com/coderlevelup/clubcheck`.
- Payload (big-endian): `B version=1, 6B event id, u32 utc seconds, s8 tz15, s16 lat*100, s16 lon*100, u16 handLo, u16 handHi, u16 camLo, u16 camHi, u16 camPoint, u8 photos, 4B install id, u8 urlLen, url bytes, u16 CRC-16/CCITT-FALSE` (init 0xFFFF, poly 0x1021). Reed-Solomon over GF(256), prim 0x11D, first root 0, 40 parity bytes.
- Strip: cells 8px, 13 columns (104px) on the left edge; rows 0-1 and 88-89 are alternating finder rows (`(row+col) % 2` white); data rows 2-87 carry bits MSB first, then pseudo-random fill; white = 1.
- Watermark: luma only, 8x8 DCT blocks outside the strip columns, coefficient (1,1), step 80, blocks with mean luma < 125 only, bit index `((bx*73856093) xor (by*19349663)) mod nbits`, QIM, soft-vote decode.
- Blur: Gaussian on soft ellipses. Person box: head diameter `d = max(min(0.5*w, 0.22*h), 28)`, centre `(cx, y1 + 0.55d)`, radii `0.75d` by `0.8d`, sigma `max(0.5d, 10)`. Face box: `d = max(w, h, 24)`, radii `0.85d` by `0.95d`, sigma `max(0.45d, 8)`; face confidence 0.15.
- Use ML Kit where it fits: **ML Kit face detection** (bundled model, not the Play-services download, so it works offline) for the face pass, run on the whole photo and on the same 3x3 tiles as counting because faces in a crowd are small (`PERFORMANCE_MODE_ACCURATE`, small `minFaceSize`, no landmarks, contours or classification). ML Kit has no multi-person detector (its object detection is coarse and capped), so people counting uses MediaPipe's Object Detector with an EfficientDet-Lite model (also Google, Apache-2.0).
- Detector models must have licences that allow shipping (Apache-2.0 preferred); no AGPL models.
- Every step ends in a commit on branch `ccr-34ddda13-jtrvxh`; push when a task completes.

## Review Focus

1. **Export leaks precise GPS via EXIF.** Camera JPEGs carry GPS. The export must contain no EXIF/APP1 metadata. (Task 9)
2. **Location permission denied or off.** The check-in must still succeed with "no location" encoded as sentinel `lat = lon = Short.MIN_VALUE` and no "area" text on the overlay. (Tasks 1, 7)
3. **Hand range typed backwards (65-55), blank, or one number.** Normalise reversed input, accept a single number as a range of one, refuse blank. (Task 5)
4. **A very bright photo.** Too few dark blocks for the watermark: export must still succeed, strip must still decode, and the result must report the weak watermark. (Task 3)
5. **Club URL too long or odd (uppercase, trailing slash, no scheme).** Normalise; reject over 100 bytes with a clear message instead of overflowing the strip. (Tasks 1, 8)

---

### Task 1: Scaffold, payload and Reed-Solomon in `:core`

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `core/build.gradle.kts`, `app/build.gradle.kts` (empty Compose shell), wrapper files
- Create: `core/src/main/kotlin/app/clubcheck/core/{Payload.kt,Crc16.kt,ReedSolomon.kt}`
- Create: `spikes/2026-11-17-event-photo/vectors.py` (writes `core/src/test/resources/vectors.json`)
- Test: `core/src/test/kotlin/app/clubcheck/core/PayloadTest.kt`

**Interfaces:**
- Produces:
  - `data class Range(val lo: Int, val hi: Int)`
  - `data class CoarseCell(val latHundredths: Int, val lonHundredths: Int) { companion object { val NONE } }`
  - `data class Payload(val eventId: ByteArray, val utcSeconds: Long, val tz15: Int, val cell: CoarseCell, val hand: Range, val camera: Range, val cameraPoint: Int, val photos: Int, val installId: ByteArray, val url: String)`
  - `fun Payload.encode(): ByteArray` (includes CRC), `fun Payload.Companion.decode(bytes: ByteArray): Payload?` (null on CRC or length failure)
  - `object ReedSolomon { const val PARITY = 40; fun encode(data: ByteArray): ByteArray; fun decode(coded: ByteArray): ByteArray? }`
  - `const val MAX_URL_BYTES = 100`

- [ ] **Step 1: Scaffold.** Install the Android SDK command-line tools under `/opt/android-sdk` with platform and build-tools for the chosen compileSdk and accept licences; `gradle wrapper`; two modules building empty. Verify: `./gradlew :core:test :app:assembleDebug` succeeds (no tests yet is fine).
- [ ] **Step 2: Write `vectors.py`** reusing `spike.py` (`make_payload`, `rsc.encode`) to emit three vectors (full payload; no-location payload; max-length URL) as hex `payload` and `coded`. Run it and commit the JSON.
- [ ] **Step 3: Write failing tests** in `PayloadTest`: `encodeMatchesPythonVectors` (hex equality for payload and for `ReedSolomon.encode(payload)`), `decodeRoundTrips`, `noLocationUsesSentinel` (`CoarseCell.NONE` encodes as `Short.MIN_VALUE` twice and decodes back to `NONE`), `corruptedCrcReturnsNull`, `rsCorrectsTwentyByteErrors` (flip 20 bytes of `coded`, `decode` returns the original), `rsFailsBeyondCapacity` (flip 25 bytes, `decode` returns null or a payload whose CRC check fails), `urlOverMaxIsRejected` (101-byte URL throws `IllegalArgumentException`).
- [ ] **Step 4: Run** `./gradlew :core:test` — Expected: FAIL (types undefined).
- [ ] **Step 5: Implement** the three files. Reed-Solomon via ZXing `GenericGF.QR_CODE_FIELD_256` (prim 0x11D, base 0), `ReedSolomonEncoder`/`Decoder`; CRC16 bitwise as in the spike. If a vector disagrees, the Python spike is the reference: adjust Kotlin, not the vector.
- [ ] **Step 6: Run** `./gradlew :core:test` — Expected: PASS.
- [ ] **Step 7: Commit** `feat(core): payload, CRC and Reed-Solomon matching spike vectors`.

### Task 2: Data strip codec

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/{RgbImage.kt,StripCodec.kt}`
- Test: `core/src/test/kotlin/app/clubcheck/core/StripCodecTest.kt`, `core/src/test/kotlin/app/clubcheck/core/JpegTestUtil.kt`

**Interfaces:**
- Consumes: `ReedSolomon`, `Payload` (Task 1)
- Produces:
  - `class RgbImage(val width: Int, val height: Int, val argb: IntArray)` with `fun luma(): FloatArray`, `fun copy(): RgbImage`
  - `object StripCodec { const val CELL = 8; const val COLS = 13; const val WIDTH_PX = 104; fun capacityBytes(height: Int): Int; fun draw(img: RgbImage, coded: ByteArray); fun read(img: RgbImage, codedLength: Int): ByteArray }`
  - Test util: `fun jpegRoundTrip(img: RgbImage, quality: Int, scale: Double = 1.0): RgbImage` (ImageIO, resizes back to original size)

- [ ] **Step 1: Failing tests:** `drawThenReadIsExact` on a 1280x720 image; `capacityIs1032BitsFor720Rows` (`capacityBytes(720) == 129`); `payloadTooLargeThrows` (coded length 130); `survivesJpegQ50` and `survivesResize60PlusJpegQ75` (decode the full payload through `ReedSolomon.decode`); `fillIsNotFlat` (unused cells are not all one colour).
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*StripCodecTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** per the strip constraint; `read` thresholds the inner 4x4 of each cell at luma 127.
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): data strip codec`.

### Task 3: Hidden watermark codec

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/{Dct.kt,Watermark.kt}`
- Test: `core/src/test/kotlin/app/clubcheck/core/WatermarkTest.kt`

**Interfaces:**
- Consumes: `RgbImage`, `StripCodec.WIDTH_PX`, `JpegTestUtil`
- Produces: `data class EmbedReport(val blocksUsed: Int, val blocksTotal: Int, val votesPerBit: Double) { val weak: Boolean }`, `object Watermark { fun embed(img: RgbImage, coded: ByteArray): EmbedReport; fun read(img: RgbImage, codedLength: Int): ByteArray }` (embed mutates `img` in place; `weak` = fewer than 3 votes per bit)

- [ ] **Step 1: Failing tests:** `roundTripsLossless`, `survivesJpegQ50`, `survivesResize60PlusJpegQ75`, `readsAfterStripRemoved` (zero the strip columns, still decodes), `brightImageReportsWeak` (all-white 1280x720: `embed` returns `weak == true` and does not throw), `invisibleEnough` (PSNR against the unmarked luma at least 38 dB on a textured test image).
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*WatermarkTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** the 8x8 DCT-II/III (orthonormal, matching OpenCV's `dct`), embed and soft-vote per the watermark constraint. Only the Y channel changes; write back by adjusting RGB equally.
- [ ] **Step 4: Run** — Expected: PASS. If `survivesResize60` fails while the spike passed, compare DCT normalisation with the spike first.
- [ ] **Step 5: Commit** `feat(core): DCT watermark codec`.

### Task 4: Compose the export image (overlay, strip, watermark, footer)

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/Export.kt`, `app/src/main/kotlin/app/clubcheck/export/OverlayRenderer.kt`
- Test: `core/src/test/kotlin/app/clubcheck/core/ExportTest.kt`

**Interfaces:**
- Consumes: `StripCodec`, `Watermark`, `Payload`
- Produces:
  - `fun overlayLines(p: Payload, title: String, zone: java.time.ZoneOffset): List<String>` — exactly three lines: title (upper case); `"17 NOV 2026 10:42 SAST"`-style local date and time, then area `"33.90S 18.40E"` or omitted when `cell == NONE`; `"HAND 55-65  CAMERA 56-74  RANGES OVERLAP"` or `"... CHECK COUNTS"`
  - `fun composeExport(blurred: RgbImage, p: Payload, drawOverlay: (RgbImage, List<String>) -> Unit, title: String): ExportResult` with `data class ExportResult(val image: RgbImage, val report: EmbedReport)`; the image is 1280x720 (the input is scaled earlier), strip drawn, then watermark embedded last
  - App-side `OverlayRenderer.draw(img, lines)` draws text with an Android `Canvas` (not unit-tested; verified by the device milestone)

- [ ] **Step 1: Failing tests:** `overlayLinesFormatsRangesAndFlag` (non-overlapping ranges 10-12 vs 40-50 gives `CHECK COUNTS`; 55-65 vs 56-74 gives `RANGES OVERLAP`), `overlayOmitsAreaWithoutLocation`, `composeThenDecodeBothLayers` (with a no-op overlay lambda; both decoders return the same payload after JPEG q75), `exportIsExactly1280x720`.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*ExportTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** The flag rule lives here as `fun flagged(hand: Range, camera: Range): Boolean` (gap greater than 3), shared with Task 5.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): export composition`.

### Task 5: Count ranges and hand-range input

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/{Counting.kt,Tiling.kt}`
- Test: `core/src/test/kotlin/app/clubcheck/core/CountingTest.kt`

**Interfaces:**
- Produces:
  - `data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val score: Float)`
  - `interface PersonDetector { fun detect(img: RgbImage, minConfidence: Float): List<Box> }`
  - `fun tileRects(w: Int, h: Int, n: Int = 3, overlap: Float = 0.2f): List<IntArray>` (x, y, w, h)
  - `fun nms(boxes: List<Box>, iou: Float = 0.5f): List<Box>`
  - `data class CameraCount(val range: Range, val point: Int, val boxesAtPoint: List<Box>)`; `fun countPeople(img: RgbImage, detector: PersonDetector): CameraCount` (runs whole image plus 9 tiles at 0.35, 0.25, 0.15, merges and NMS per level, `range = (low, high)`, `point` = count at 0.25)
  - `fun parseHandRange(text: String): Result<Range>`; `fun flagged(hand: Range, camera: Range): Boolean`
  - `fun mergeEvent(photos: List<CameraCount>): CameraCount` (per field maximum across photos, never the sum)

- [ ] **Step 1: Failing tests:** `tilesCoverImageWithOverlap` (1280x720 gives 9 rects, union covers the image, neighbours overlap by about 20%); `nmsDropsDuplicates`; `countPeopleUsesThreeLevels` with a fake detector returning boxes above/below thresholds (expect `range == Range(56,74)`-style monotone result, `low <= point <= high`); `mergeEventTakesMaxNotSum`; `parseHandRange` cases: `"55-65"`, `"60"` gives `Range(60,60)`, `"65-55"` gives `Range(55,65)`, `""` and `"abc"` give failure, `"-5"` gives failure; `flaggedOnlyWhenGapOverThree` (gap exactly 3 is not flagged; 4 is).
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*CountingTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** with the constants from Global Constraints. Move `flagged` here and make Task 4 call it.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): tiled count ranges and hand range parsing`.

### Task 6: Head and face blur

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/{Gaussian.kt,HeadBlur.kt}`
- Test: `core/src/test/kotlin/app/clubcheck/core/HeadBlurTest.kt`

**Interfaces:**
- Consumes: `Box`, `RgbImage`
- Produces: `data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val sigma: Float)`; `fun headEllipse(person: Box): Ellipse`; `fun faceEllipse(face: Box): Ellipse`; `fun blurHeads(img: RgbImage, people: List<Box>, faces: List<Box>): RgbImage` (new image; originals untouched)

- [ ] **Step 1: Failing tests:** `headEllipseMatchesSpikeFormula` (box 100x400 at (0,0): `d = min(50, 88) = 50`, centre `(50, 27.5)`, radii `37.5` by `40`, sigma `25`); `tinyBoxUsesMinimumDiameter` (d is 28); `faceEllipseMatchesFormula`; `blurChangesPixelsInsideNotFarOutside` (checkerboard image: variance inside the ellipse drops by at least 90%, pixels 4 sigma away unchanged); `blurDoesNotMutateInput`.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*HeadBlurTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** a separable Gaussian on an ROI and a feathered ellipse mask (feather sigma 0.25 of the blur sigma, minimum 2).
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): ellipse Gaussian head and face blur`.

### Task 7: Coarse location and time

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/Place.kt`
- Test: `core/src/test/kotlin/app/clubcheck/core/PlaceTest.kt`

**Interfaces:**
- Consumes: `CoarseCell`
- Produces: `fun snapToCell(lat: Double, lon: Double, grid: Double = 0.05): CoarseCell` (nearest grid centre, in hundredths of a degree); `fun tz15(offsetSeconds: Int): Int`; `fun cellLabel(c: CoarseCell): String?` (`"33.90S 18.40E"`, `null` for `NONE`)

- [ ] **Step 1: Failing tests:** `snapsCapeTownToExpectedCell` (-33.9249, 18.4241 gives `CoarseCell(-3925, 1840)`... use the grid rule: nearest 0.05 multiple, so `-33.90`, `18.40`; assert the exact hundredths from the formula), `snapNeverReturnsFinerThanGrid` (random 1000 points: result mod 5 hundredths is 0), `tz15ForSast` (7200 s gives 8), `tz15ForIndia` (19800 s gives 22), `labelNoneIsNull`, `labelHandlesHemispheres`.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*PlaceTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): coarse place snapping and timezone`.

### Task 8: Local store, club URL and upload queue

**Files:**
- Create: `core/src/main/kotlin/app/clubcheck/core/ClubUrl.kt`; `app/src/main/kotlin/app/clubcheck/data/{CheckInEntity.kt,ClubEntity.kt,Dao.kt,AppDb.kt}`, `app/src/main/kotlin/app/clubcheck/upload/{CloudApi.kt,UploadWorker.kt}`
- Test: `core/src/test/kotlin/app/clubcheck/core/ClubUrlTest.kt`, `app/src/test/kotlin/app/clubcheck/upload/UploadQueueTest.kt`

**Interfaces:**
- Produces: `fun normaliseClubUrl(input: String): Result<String>` (lower-case host, drop scheme `https://` and a trailing slash, trim; failure over `MAX_URL_BYTES`); `fun parsePageMeta(html: String): Pair<String?, String?>` (title from `og:title` then `<title>`; summary from `og:description` then meta description); `interface CloudApi { suspend fun postCheckIn(payload: ByteArray, image: ByteArray): Boolean }`; Room entities `CheckIn(id, eventId, payloadHex, imagePath, state)` and `Club(url, title, summary)`; `UploadWorker` posts queued check-ins, idempotent on `eventId`; `fun originalsToPurge(files: List<StoredOriginal>, nowMs: Long, keepDays: Int = 7): List<StoredOriginal>` with `PurgeOriginalsWorker` (daily) deleting them

- [ ] **Step 1: Failing tests:** `normaliseHandlesCaseSchemeSlash` (`"HTTPS://RSVP.withgoogle.com/events/x/"` gives `"rsvp.withgoogle.com/events/x"`), `normaliseRejectsOver100Bytes`, `normaliseRejectsEmpty`, `pageMetaPrefersOgTags`, `pageMetaFallsBackToTitle`; `uploadRetriesAfterFailureAndNeverDuplicates` (fake `CloudApi` fails once, then succeeds; the queue marks the check-in sent exactly once); `purgeSelectsOnlyOriginalsOlderThanSevenDays` (6 days 23 hours kept, 7 days 1 hour purged, blurred exports never selected).
- [ ] **Step 2: Run** `./gradlew :core:test :app:testDebugUnitTest --tests '*ClubUrlTest*' --tests '*UploadQueueTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** Fetching the page happens in the app over OkHttp with a short timeout; offline stores the URL and fetches the title later.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat: club url, local store and upload queue`.

### Task 9: Android adapters and JPEG export without metadata

**Files:**
- Create: `app/src/main/kotlin/app/clubcheck/ml/{MediaPipePersonDetector.kt,MlKitFaceDetector.kt}`, `app/src/main/kotlin/app/clubcheck/export/{ExportWriter.kt,ImageConvert.kt}`, `app/src/main/kotlin/app/clubcheck/location/CoarseLocation.kt`
- Test: `app/src/test/kotlin/app/clubcheck/export/ExportWriterTest.kt`

**Interfaces:**
- Consumes: `PersonDetector`, `composeExport`, `blurHeads`, `snapToCell`
- Produces: `MediaPipePersonDetector : PersonDetector` (EfficientDet-Lite class model bundled in assets, Apache-2.0); `MlKitFaceDetector.detect(img: RgbImage): List<Box>` (bundled `com.google.mlkit:face-detection`; tiles as in Global Constraints; ML Kit reports no per-face score, so every returned face is blurred; padded as in the face-ellipse rule); `fun encodeJpegNoMetadata(img: RgbImage, quality: Int = 92): ByteArray`; `suspend fun CoarseLocation.current(): CoarseCell` (returns `NONE` when permission is denied or location is off)

- [ ] **Step 1: Failing tests:** `exportBytesContainNoExifOrGps` (encode an image, assert no `FFE1`/`Exif` marker and no `GPS` string anywhere in the bytes); `exportAppliesCameraRotation` (an EXIF-rotated source ends up upright, and the output carries no orientation tag); `locationDeniedYieldsNone`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ExportWriterTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** Re-encode from raw pixels so no source metadata can carry over; read EXIF orientation first, rotate, then discard. Detector adapters are verified on a device (Task 11); keep them thin.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(app): detector adapters and metadata-free export`.

### Task 10: Compose UI and view-models

**Files:**
- Create: `app/src/main/kotlin/app/clubcheck/ui/{CaptureScreen.kt,ReviewScreen.kt,ShareScreen.kt,CheckInViewModel.kt,Nav.kt}` (navigation via Navigation Compose, not the Fragment-based Navigation component, which is in maintenance mode per the Compose-first guidance), `app/src/main/AndroidManifest.xml` (camera, coarse location only; no internet beyond the upload worker and page fetch)
- Test: `app/src/test/kotlin/app/clubcheck/ui/CheckInViewModelTest.kt`

**Interfaces:**
- Consumes: everything above
- Produces: `class CheckInViewModel` with `fun addPhoto(img: RgbImage)`, `fun setClubUrl(text: String)`, `fun setHand(text: String)`, `fun setLocationEnabled(on: Boolean)`, `val state: StateFlow<CheckInState>`, `suspend fun export(): ExportOutcome` (`Ready(jpeg: ByteArray, flagged: Boolean, weakWatermark: Boolean)` or `Invalid(reason)`)

- [ ] **Step 1: Failing tests** (fake detector, faces, location): `exportBlockedWithoutHandRange`, `exportBlockedWithoutClubUrl`, `twoPhotosUseMaxCameraCount`, `locationOffExportsWithoutArea`, `weakWatermarkIsSurfaced` (bright photo: `weakWatermark == true`, strip still decodes), `originalsAreNotPartOfTheOutput` (blurred pixels differ from the source inside a detected head).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*CheckInViewModelTest*'` — Expected: FAIL.
- [ ] **Step 3: Implement** thin screens: Capture (CameraX multi-photo), Review (counts shown as ranges, hand range field, club URL field with fetched title, location switch), Share (share sheet via FileProvider, JPEG only). Hand range field shows the parse error from Task 5.
- [ ] **Step 4: Run** — Expected: PASS; also `./gradlew :app:assembleDebug` succeeds.
- [ ] **Step 5: Commit** `feat(app): capture, review and share screens`.

### Task 11: Real-device milestone (the user runs this)

**Files:** Create `docs/device-test.md`

- [ ] **Step 1: Write the checklist** in `docs/device-test.md`: install the debug APK (`app/build/outputs/apk/debug/`); take two photos of a real group; confirm the camera range is plausible against a hand count; confirm heads are blurred and no face survives on zoom; share through WhatsApp; run the decoder on the received copy (`StripCodec` and `Watermark` via a small `:core` CLI or test) and record the result; check the photo's EXIF shows nothing.
- [ ] **Step 2: Add** `core/src/main/kotlin/app/clubcheck/core/DecodeCli.kt` (`main(args)`: path in, payload JSON out) with a test `DecodeCliTest.decodesSpikeWhatsAppImage` that decodes `spikes/2026-11-17-event-photo/images/received-via-whatsapp.jpg` and checks the club URL and camera range `56-74`.
- [ ] **Step 3: Run** `./gradlew :core:test --tests '*DecodeCliTest*'` — Expected: PASS (proves the Kotlin codec reads a real WhatsApp recompression).
- [ ] **Step 4: Commit** `docs: device test checklist; decode the real WhatsApp image`.

## Notes for the executor

- The spike's WhatsApp image is the best test of the Kotlin codec; Task 11 Step 2 should pass before anything depends on the codec in the UI. If it fails, fix `:core` before continuing.
- Counting accuracy is not established. Do not tune thresholds; calibration on hand-counted photos is a separate task after this plan.
- The face-blur pass found one missed profile in the spike; treat blur as unfinished until the device checklist is clean.
