# Device test checklist (run on a real Android phone)

Everything below is what the JVM tests cannot prove: the camera, the on-device detectors, WhatsApp, and
EXIF. Tick each item and note the result. The detector model is not in the repo (see step 1).

## 1. Get the people-counting model (once)
The container this was built in cannot reach Google's model host, so the model is not committed.
Download MediaPipe's EfficientDet-Lite0 (Apache-2.0) and put it in the app's assets:

```
curl -L -o app/src/main/assets/efficientdet_lite0.tflite \
  https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite
```
Without it the app shows "People counting model ... is missing" when you add a photo.

## 2. Build and install
`./gradlew :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`
(`adb install -r ...` or copy it to the phone). Android 8.0 (API 26) or newer.

## 3. Check-in
- [ ] Allow the camera. Take two photos of a group (try one close group and one wider room).
- [ ] The camera count shows a range and a best guess. Write down the real number of people and the range:
      photo 1 real ___ range ___; photo 2 real ___ range ___.
- [ ] Enter your own count (a number or a range), a club web address, and see its title appear (online).
- [ ] Turn the area switch on and allow coarse location; then repeat with it off and with permission denied.
- [ ] Make the shareable image. It shows the strip on the left, the text overlay, the repo footer.
- [ ] The data strip is a column beside the photo (it does not cover anyone at the left edge). The club link is readable in the header.
- [ ] Try a very long club link (about 120 characters): the header wraps it onto more lines, nothing runs off the edge, the strip is wider, and the image still decodes after WhatsApp.
- [ ] Zoom in on the image. Every face and head is blurred. Note any that are not: ___

## 4. Share and decode
- [ ] Share through WhatsApp to yourself; save the received photo to your computer.
- [ ] Copy the received image to your computer and decode it: put it in `spikes/2026-11-17-event-photo/images/`, then run
      `./gradlew :core:test --tests '*DecodeCliTest*'` for the reference image, or run `app.clubcheck.core.DecodeCliKt` with the
      image path (for example from IntelliJ) to print the data found in your own file.
- [ ] Both layers decode (`viaStrip` and `viaWatermark` true, `layersAgree` true). Note the app used and the file size.
- [ ] Repeat once through another app (Telegram, Signal, email) and once as a screenshot.

## 5. Privacy
- [ ] The shared image's details (Files app, "Details" or an EXIF viewer) show no location or camera data.
- [ ] After sending, the original camera photos exist only inside the app's private folder (not in Gallery).
- [ ] Airplane mode: a check-in still works and the image can still be shared.
