# Club Check-in: design

Status: DRAFT for approval. Intended home: `docs/superpowers/specs/` in the new repo.

## Intent and why
Community clubs and events need a cheap, privacy-safe way to show they are running and how many
people turn up. Two audiences: (1) finding active clubs, (2) philanthropy monitoring and
evaluation (M&E), where funders want attendance evidence without collecting faces.

An organiser takes one or more photos at a session. The phone counts the people on-device. The
organiser also types their own hand count. The app produces a blurred, shareable image with the
stats drawn on it and the same data hidden inside it. Check-ins upload to a cloud service, and can
also travel as the image alone, so the image itself is the record.

## Goals
- On-device people counting; the original photo never leaves the phone.
- Hand count vs camera count is recorded together; large gaps are flagged for M&E.
- Club identity is just a URL; the app fetches its title and summary.
- Works with no connectivity: everything is saved locally and uploads are queued.
- A shared image carries its own data and survives chat-app recompression (WhatsApp,
  Messenger, etc.), so it can be ingested later even if it never went through the app's upload.
- Cloud view: which clubs are active (frequency, typical size) and an M&E export.

## Non-goals
- Identifying individuals, face recognition, or any per-person tracking.
- Accounts, logins, payments, messaging.
- Cryptographic proof of authenticity (a checksum detects accidents and casual edits; it does not
  stop a determined forger).
- Exact head counts. The camera count is an estimate.

## Anti-goals (failure even if it "works")
- Any recognisable face reaching the cloud or leaving the phone in a shared image.
- A shared image whose hidden data dies after one chat-app hop.
- Needing the cloud to make a check-in.

## Constraints
- Flutter (one codebase, Android and iOS). Built and tested from a Linux container: the codec,
  counting logic and cloud can be tested here; on-device camera and ML runtime cannot. The first
  real phone test is a milestone for the user.
- Chat apps recompress to JPEG (typically quality 60-80) and may downscale (long edge about 1600px
  on some apps).

## Approaches considered
- **A. Native cross-platform app (Flutter), on-device ML.** Chosen by the user. Best camera, speed
  and offline behaviour; costs store builds and no device testing from this container.
- **B. Installable web app (PWA).** Fastest and fully testable here, but weaker offline and camera
  on iOS. Rejected by the user in favour of A.
- **C. Godot.** Weak camera and ML support. Rejected.

Image embedding: the user chose the **recompression-proof** route over lossless-only hidden pixels
(which chat apps destroy), plus a visible **data strip** as a cross-check.

## The design

### Capture and counting (my calls)
- One check-in = one event with 1..N photos. Camera count for the event = the maximum count
  across its photos (not the sum; avoids double-counting people in overlapping shots).
- Detector: a TFLite person detector (a small EfficientDet-Lite or YOLO-class model). It counts
  people including the backs of heads and partial bodies.
- Organiser enters a hand count. Flag = hand and camera differ by more than a threshold
  (starting point: >25% and >3 people; tunable server-side).

### Blurring (my call)
- For every detected person box, blur or pixelate the top third (head region); also run face
  detection and blur those. Heavy enough that faces cannot be recovered. Done before anything is
  saved for sharing or uploaded.
- Originals stay on the device only, with an optional auto-delete (default 7 days).

### Club identity
- Organiser pastes or scans a URL. The app (or the cloud, when online) reads the page title and
  summary (`og:title`, `og:description`, falling back to `<title>` and meta description) and caches
  them. Club id = normalised URL. Offline: store the URL and fetch the title later.
- The cloud fetcher must refuse private, loopback and link-local addresses (SSRF protection),
  cap response size and time, and follow only a few redirects.

### The shareable image
Fixed export size so chat apps do not rescale it (target long edge 1280px, JPEG-safe).
1. **Visible overlay:** club title, date, hand count, camera count, a flag marker if the counts
   disagree, in a clear pixel style. Readable even if everything else fails.
2. **Film-soundtrack data strip:** a vertical strip along one edge made of large coarse blocks
   (chunky, high-contrast, in the dark pixel style), carrying the full payload with strong
   error correction, plus fiducial markers so a decoder can find and de-skew it. Large cells make
   it survive JPEG and mild resizing. This is the primary decode path.
3. **Hidden watermark:** the same payload embedded in the image's dark regions using a
   frequency-domain scheme (quantisation-index modulation on mid-frequency coefficients of 8x8
   blocks, repeated across many blocks, Reed-Solomon coded) so it tolerates JPEG recompression and
   modest resizing. This is the cross-check, and survives even if the strip is cropped off.
4. Decode order: strip first, then watermark; if both decode they must agree, else flag.

### Payload (compact, versioned)
version, club URL (compressed), event id (random), timestamp, hand count, camera count, photo
count, anonymous install id (random, not tied to a person), checksum. Size target about 100-150
bytes. If the URL does not fit the watermark capacity, the watermark carries a hash of it and the
strip carries the full URL; the builder measures capacity and decides.

### Cloud (my calls)
- Cloudflare Workers + D1 (check-in rows, clubs) + R2 (blurred images, for audit).
- Endpoints: `POST /checkin` (app upload: payload + blurred image), `POST /ingest` (a shared image
  in, payload decoded server-side, same record created), `GET /clubs` (activity),
  `GET /export` (M&E CSV). Idempotent on event id so duplicates from re-sends collapse.
- Dashboard: simple web page listing clubs by recent activity, check-in counts, average size, and
  the flagged check-ins.

### Offline-first
Local SQLite queue; uploads retry in the background; the share sheet works with no connection.

## Testing
- Codec round-trip tests: embed, then JPEG at q=90/75/60, resize to 80%, crop the strip; assert
  the payload still decodes and ECC corrects errors. This is the key risk and needs a test corpus
  of varied photos (bright, dark, noisy).
- Counting: fixture images with known counts; blurring tests that a face detector finds nothing
  afterwards.
- Cloud: unit tests for ingest, idempotency, SSRF blocking.
- Device milestone (user): run on a real phone and share through WhatsApp to confirm decode.

## Left to the builder
Model choice and exact blur strength; watermark parameters (embedding strength, repetition,
block selection) tuned against the recompression test corpus; strip geometry and cell size;
payload byte layout; flag thresholds; dashboard layout.

## Open / risks
- Dark regions limit capacity; very bright photos give little room. Mitigation: the strip, and
  lowering embedding strength threshold on bright images.
- Social apps may strip metadata entirely; none of the design relies on metadata.
- The hidden scheme is not secure against deliberate removal.
