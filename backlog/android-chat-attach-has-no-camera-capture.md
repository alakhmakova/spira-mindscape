# Android AI chat: the paperclip can only pick from the gallery, not take a photo

- **ID:** BUG-029
- **Status:** 🔧 In progress
- **Reported by:** User (2026-08-07)
- **Area:** Android — AI chat composer (`ui/ai/AiChatScreen.kt`, attachment picker)
- **Type:** Defect (missing path, parity gap with the intent behind the feature)

## Summary

Tapping the paperclip in the Android chat composer opens **only the file/gallery picker**. There
is no way to **take a photo with the camera** and attach it, which is the fastest route on a
phone — photographing a document, a whiteboard, or a receipt and asking the assistant to read it
is exactly the case the OCR work was built for.

The web has no equivalent gap because a desktop browser's file dialog is the only sensible
source there; on a phone the camera is the primary one.

## Steps to reproduce

1. Open a goal on Android → open the AI assistant (footer sparkle or swipe up on the footer).
2. Tap the **paperclip** in the composer.
3. Observe: only a document/gallery chooser appears. Nothing offers the camera.

## Root cause

The composer launches a content picker (`ActivityResultContracts.GetContent` / `OpenDocument`)
only. No `TakePicture` contract is registered, so the camera is never an option, and no
`FileProvider` output URI is prepared for it to write into.

## Fix approach

- Offer a **choice** on tap — "Take a photo" / "Choose a file" — using the app's own menu
  (`SpiraDropdownMenu`), not a system chooser, per the UI conventions.
- Register `ActivityResultContracts.TakePicture` with an output `Uri` from a `FileProvider`
  authority declared in the manifest; capture to the app's cache dir so nothing lands in the
  user's gallery unless they want it there.
- Feed the captured image through the **same downscale path** as gallery images — see
  `camera-photo-attach-low-memory.md` (BUG-0xx): full-resolution phone photos OOM'd before, so
  the capture must be sampled down before it becomes a base64 attachment.
- `CAMERA` permission is only required if the manifest declares it; using `TakePicture` with a
  FileProvider URI does not need the permission unless declared. Prefer not declaring it.

## How to verify fixed

- Tap the paperclip → both options appear.
- "Take a photo" opens the camera, and the shot arrives as a chip above the input.
- Send it; the assistant receives the image (a vision-capable model reads it).
- The attachment is downscaled — check the request size and that a large photo doesn't OOM.
- Compose UI test: the menu offers both entries; a unit test covers the downscale of a
  camera-sized bitmap.

## Resolution

_(fill in when done)_
