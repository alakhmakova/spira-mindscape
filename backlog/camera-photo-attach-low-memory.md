# Attaching a photo from the camera triggers "low memory" on mobile

- **ID:** BUG-022
- **Status:** 🔧 In progress — memory-safe decode implemented; awaiting **on-device** confirmation
  (a real phone camera + real low-memory conditions; can't be reproduced headlessly).
- **Reported by:** User (mobile web, attaching a photo via the camera)
- **Area:** Frontend web — AI chat attachments (`src/components/ai/AiPanel.tsx`, `downscaleImage`)
- **Severity:** Medium–High (attaching a camera photo can crash/limit the tab on a phone)

## Summary

Attaching a photo taken with the phone **camera** brings back a "low memory" error (it had been
improved earlier by the "photo attach downscale" work, but camera captures still hit it). Gallery
images are usually fine; camera captures are the trigger.

## Steps to reproduce

1. On a phone browser, open the AI chat → attach → choose the **camera**.
2. Take a full-resolution photo and attach it.
3. Observe a low-memory error / the image failing to attach.

## New evidence (2026-08-03) — the fix did NOT settle it

The owner reports the crash **still comes back** after the memory-safe decode shipped, on and off
as before, and suspects it may be tied to Gemini. Asked what they actually see: **the page dies and
reloads, with a system toast from the phone.**

That reframes everything below. A renderer killed by the OS never reaches a `catch`, so none of the
app's own messages ("Couldn't read …", "Image too large to process on this device") belong to this
crash — they are a different, survivable failure. Nothing reaches the server either, so the Gemini
suspicion, if real, points at a long streamed answer rather than at attachment. And a serious
alternative cause is still untested: on Android the camera app is heavy enough that the low-memory
killer can evict a background browser tab **while the photo is being taken** — before any of our
code runs, where no decode limit could help.

The suspected cause below is therefore still *suspected*, and so is the value of `IMAGE_MAX_DIM`.
The two candidate causes need opposite fixes and are indistinguishable with what the app records
today. **Diagnosis plan: `specs/2026-08-03-attachment-crash-diagnostics/requirements.md`** —
crash-surviving breadcrumbs in `localStorage`, reported after the reload, plus `adb logcat` /
`chrome://crashes` from the device, plus three no-code experiments (gallery vs camera, camera-app
first, provider by provider) that can settle it before any code is written.

## Root cause (suspected — needs on-device confirmation)

`downscaleImage` decodes the **whole** image before shrinking it:

```
const bitmap = await createImageBitmap(file);   // decodes the FULL image into memory
... draw to a scaled canvas ...
```

A modern phone camera photo is ~12 MP, i.e. ~48 MB once decoded to an uncompressed bitmap. On a
memory-constrained mobile browser, that peak during `createImageBitmap` can OOM the tab **before**
the downscale ever helps. Camera captures are higher-res than typical gallery picks, so they hit it
where gallery images don't.

Two aggravating factors:
- On failure, `downscaleImage` falls back to `readFileAsDataUrl(file)` — the **raw** multi-MB file
  as base64. If the failure was an OOM during decode, loading the raw bytes doesn't help and can
  keep memory high.
- `IMAGE_MAX_INPUT_BYTES` is 25 MB, which permits very large captures whose *decoded* size is far
  larger than the byte size suggests.

## Fix approach (proposed)

Bound peak memory so the full-size bitmap is never materialised:

1. Decode-and-downscale in one step via `createImageBitmap(file, { resizeWidth, resizeHeight,
   resizeQuality: "medium" })` (compute the target box from the source aspect; where only a max
   edge is known, preserve aspect). This lets the browser avoid holding the full bitmap.
2. **Don't** fall back to the raw full file for large images — if the memory-safe decode fails and
   the file is large, reject with a clear message ("This photo is too large to attach — try a
   smaller one") instead of loading raw bytes that re-trigger the OOM.
3. Consider lowering `IMAGE_MAX_INPUT_BYTES` and/or warning above a threshold.
4. Promptly revoke any object URL and clear the canvas (already done for the canvas) to release
   memory fast.

## How to verify fixed

- On a real phone, attach a full-res camera photo repeatedly — no low-memory error, and the stored
  attachment is a small downscaled JPEG.
- (Automated coverage is limited: a headless browser can't drive the camera, and OOM is
  device/memory-specific — this needs **on-device** verification by the owner.)

## Resolution (partial — implemented, needs on-device sign-off)

Implemented the memory-safe decode in `src/components/ai/AiPanel.tsx`:
- Added `readImageSize(file)` — reads pixel dimensions straight from the PNG IHDR / JPEG SOF header
  (first ≤64 KB, no full decode).
- `downscaleImage` now, when the header gives dimensions above `IMAGE_MAX_DIM`, decodes **directly
  at the reduced size** via `createImageBitmap(file, { resizeWidth, resizeHeight, resizeQuality })`,
  so the full-size bitmap is never materialised (Chrome does scaled JPEG decode — exactly the
  camera case). A draw-time scale remains as the safety net when the header is unreadable.
- The decode-failure fallback no longer loads the raw multi-MB file for large images (> 5 MB) — it
  now rejects with an error instead of piling base64 onto the memory pressure.

Verified headlessly (Playwright): a generated 2000×1500 image attaches, downscales to 1600×1200,
and previews — end to end, no full-size intermediate. `npm test` 119/119, `tsc`/lint clean.

**Still needs:** confirmation on a real phone with a real camera capture under real memory
constraints — a genuine OOM that *crashes* the tab is uncatchable in JS, so only on-device testing
can close this. Left In progress until the owner confirms.

## Related

- **BUG-017** (`chat-direct-file-attachments.md`) — the attachment feature.
- **BUG-021** (`chat-image-preview-lost-after-sync.md`) — image preview; separate issue, fixed.
