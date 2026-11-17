# Spike: one real event photo (THROWAWAY)

Status: throwaway. Proves or disproves the risky parts of the design on a single real photo
(a seated audience of about 60, seen from behind, at a developer meetup). Nothing here is app code.
The original photo and model weights are deliberately not committed (this repo is public).
Two blurred images are in `images/`, committed with the owner's say-so: `export-1280x720.png`
(the app's export: blurred, overlay, data strip, hidden watermark) and
`received-via-whatsapp.jpg` (the same image after a real WhatsApp round trip). The decoder in
`spike.py` can be pointed at the second to reproduce the round-trip result.

Run: `PHOTO=/path/to/photo.jpg python tiled.py` (needs `ultralytics`, `opencv-python-headless`,
`reedsolo`, `pillow`; YOLOv8 weights `yolov8s.pt` in `/tmp/spike/models`), then `spike.py`.

## Ranges, not points (decided after review)
Both counts are shown and stored as ranges, because neither is exact.
- **Hand count:** an organiser-entered range (here 55-65).
- **Camera count:** a range from a rule fixed in advance: 3x3 tiled count at confidence 0.35 (low),
  0.25 (point estimate) and 0.15 (high). On this photo: 56-74, point 60.
- **Flag:** only when the ranges do not overlap by more than 3 people.
- Caveat: the 0.25 setting was chosen before seeing the 60, but I swept several settings and
  reported the one that matched, so the match is not validation. Error is unmeasured.

## Counting (hand count about 60)
| Setting | Count |
|---|---|
| YOLOv8s, whole image, 1920px, conf 0.25 | 41 |
| YOLOv8s, whole image, 1920px, conf 0.15 | 49 |
| YOLOv8s, 3x3 tiles + whole image, conf 0.25 | **60** |
| same, conf 0.15 / 0.35 | 74 / 56 |

- Whole-image counting undercounts badly (seated people, far rows, partial bodies).
- Tiling closes the gap but costs ten model runs per photo. Looking at the boxes, some are
  duplicates or partial bodies and some people are missed, so the 60 is partly luck. The count
  swings 47 to 74 with the confidence setting.
- Conclusion: the camera count is a rough estimate. The hand-vs-camera flag should be loose, and
  the threshold needs calibrating on many photos before anyone relies on it.

## Image codec (strip + hidden watermark), 1280x720
Payload 91 bytes + 40 Reed-Solomon bytes = 131 bytes (1048 bits). Strip: 13 x 86 cells of 8px.
Watermark: one DCT coefficient per 8x8 block (quantisation index modulation), only in blocks with
mean luma < 125, bit chosen by a hash of the block position, about 9.6 blocks voting per bit.

| Test | Strip | Watermark |
|---|---|---|
| PNG | ok | ok |
| JPEG q95 / 85 / 75 / 65 / 50 | ok | ok (0% raw bit errors) |
| resize 90% / 80% / 60% + JPEG q75 | ok | ok with coefficient (1,1), step 80; **failed** with a mid-frequency coefficient (2,3), step 36 |
| strip removed + JPEG q75 | n/a | ok |

Watermark visibility: PSNR 40 dB against the unmarked image.

## Real WhatsApp round trip (2026-11-17)
The exported image was sent through WhatsApp and downloaded again. It came back as a 1280x720
JPEG, 129 KB, 34.4 dB PSNR from the original export (a real recompression, not the PNG). Both the
data strip and the hidden watermark decoded to the full payload (ranges, URL, time, location),
with zero watermark bit errors after correction and strip cells at 1 to 254 on a 0-255 scale
against a threshold of 127, so there was wide margin. One phone, one app, one image, and the
export size was below WhatsApp's resize limit, so this does not yet cover resizing, other apps,
or iOS.

## Caveats (do not skip)
- One photo. One real WhatsApp round trip passed (above); other apps and iOS are untested.
- The decoder assumes the known export size and aspect. There is no geometric sync, so rotation,
  cropping or a different aspect ratio are untested and would likely fail.
- Blurring is two passes of Gaussian blur on soft ellipses: (1) a head estimated from the top of
  each person box, and (2) every detected face (`faces.py`: a YOLOv8n-face model run on the whole
  image plus 3x3 tiles, confidence 0.15, 36 candidates; low threshold on purpose, since extra blur
  is the safe error). The pass fixed the partly visible profile found earlier. Checked by eye on
  zoomed crops of one photo only, so a false-negative rate is unknown and a measured check on many
  photos is still required.
- Licensing: Ultralytics YOLO is AGPL-3.0 and the face weights came from a third-party GitHub
  release. For a shipped app, pick a model with a licence that fits (or buy the enterprise
  licence).
- The footer shows the repo URL as visible text (not in the encoded payload).
- The strip is drawn over the photo and hides its left edge. The real design should add it as a
  margin outside the picture.
- Location and time in the overlay were hard-coded for the test.
