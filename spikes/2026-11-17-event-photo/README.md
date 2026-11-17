# Spike: one real event photo (THROWAWAY)

Status: throwaway. Proves or disproves the risky parts of the design on a single real photo
(a seated audience of about 60, seen from behind, at a developer meetup). Nothing here is app code.
Photos and model weights are deliberately not committed (this repo is public).

Run: `PHOTO=/path/to/photo.jpg python tiled.py` (needs `ultralytics`, `opencv-python-headless`,
`reedsolo`, `pillow`; YOLOv8 weights `yolov8s.pt` in `/tmp/spike/models`), then `spike.py`.

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
Payload 85 bytes + 40 Reed-Solomon bytes = 125 bytes (1000 bits). Strip: 12 x 86 cells of 8px.
Watermark: one DCT coefficient per 8x8 block (quantisation index modulation), only in blocks with
mean luma < 125, bit chosen by a hash of the block position, about 9.6 blocks voting per bit.

| Test | Strip | Watermark |
|---|---|---|
| PNG | ok | ok |
| JPEG q95 / 85 / 75 / 65 / 50 | ok | ok (0% raw bit errors) |
| resize 90% / 80% / 60% + JPEG q75 | ok | ok with coefficient (1,1), step 80; **failed** with a mid-frequency coefficient (2,3), step 36 |
| strip removed + JPEG q75 | n/a | ok |

Watermark visibility: PSNR 40 dB against the unmarked image.

## Caveats (do not skip)
- One photo. JPEG was simulated with OpenCV, not by WhatsApp itself. Real phone test still needed.
- The decoder assumes the known export size and aspect. There is no geometric sync, so rotation,
  cropping or a different aspect ratio are untested and would likely fail.
- Blurring uses the top part of each person box only. No face detector ran, so some faces may
  survive; this needs a real face pass and a check.
- The strip is drawn over the photo and hides its left edge. The real design should add it as a
  margin outside the picture.
- Location and time in the overlay were hard-coded for the test.
