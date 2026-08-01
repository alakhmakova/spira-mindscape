# Chat image attachment doesn't preview (bytes wiped by transcript sync)

- **ID:** BUG-021
- **Status:** ✅ Fixed — see Resolution. Pending manual commit by the user.
- **Reported by:** User (while testing the new "tap an image attachment to preview it" feature)
- **Area:** Frontend web — AI chat (`src/components/ai/AiPanel.tsx`)
- **Severity:** Medium (feature silently stops working seconds after use)

## Summary

Tapping the name of an image attached in the AI chat was supposed to open a preview of the image,
but nothing happens (the chip is not clickable). It worked for a moment right after attaching, then
stopped.

## Steps to reproduce

1. Open the AI chat, attach an image, and send the message.
2. Wait a few seconds (~4s).
3. Tap the attachment chip (the file name) in the sent message.

**Expected:** the image opens in a preview modal.
**Actual:** nothing happens — the chip is a plain, non-clickable label.

## Root cause (confirmed via Playwright)

The image chip only becomes a preview button when the attachment still has its bytes
(`a.dataUrl`). The transcript is **stripped of attachment bytes before it is persisted / synced**
(`messagesForStore` sets `dataUrl: ""`, to keep localStorage + the synced server blob small).

The cross-device sync poll (every 4s) fetches the server transcript and, on a genuine change,
adopted it wholesale with `setMsgs(serverMsgs)`. That server copy has **empty** `dataUrl`s, so
within ~4s of sending, the in-memory message's image bytes were overwritten with `""` — and the
chip stopped being previewable. Reproduced with a headless Playwright run: the `Preview …` button
existed at send time but was gone 6s later (count 0), exactly one poll cycle in.

## Fix approach

When adopting the server transcript, **preserve local attachment bytes**: a `mergeAttachmentBytes`
helper carries any `dataUrl` we still hold in memory into the incoming (stripped) copy, matched by
message id + attachment index (guarded by name). The poll now does
`setMsgs((prev) => mergeAttachmentBytes(prev, serverMsgs))` instead of `setMsgs(serverMsgs)`.

This keeps the storage/sync payload small (bytes are still stripped on the wire) while the image
stays previewable for the whole session. A chat reloaded from scratch (fresh mount, no bytes in
memory) still falls back to a plain chip — that limit is by design and unchanged.

## How to verify fixed

1. Attach an image, send, wait >4s (past a sync tick), tap the chip → the preview opens.
2. Automated check (Playwright, this session): after 6s the `Preview …` button still exists
   (count 1) and the modal `<img>` reports a non-zero `naturalWidth` (the data URL rendered).
3. `npm test` 119/119; `tsc` clean; `AiPanel.tsx` lint clean (2 pre-existing warnings only).

## Resolution

Fixed in `src/components/ai/AiPanel.tsx`:
- Added `mergeAttachmentBytes(prev, next)` (near `messagesForStore`).
- The cross-device transcript poll adopts with `mergeAttachmentBytes` so a synced (stripped) copy
  no longer blanks out an image attached this session.
- The preview UI itself (the chip becoming a button + the `ContentModal` image mode) was added
  earlier in the same session; this makes it actually survive sync.
- **Preview before sending too:** the pending attachment chip in the composer is now clickable for
  images (the ✕ still removes). `Composer` takes an `onPreviewImage` callback that opens the same
  panel-level `ContentModal`; the composer holds the bytes in state, so no sync concern there. (The
  user was originally tapping the pre-send chip, where preview didn't exist yet.)

Verified with two Playwright reproductions — sent-message chip still previewable 6s after send
(past a sync tick), and the pre-send composer chip opens the modal with the image loaded
(`naturalWidth` ≠ 0) — plus `npm test` 119/119, `tsc` clean, lint clean.

## Related

- **BUG-017** (`chat-direct-file-attachments.md`) — added the direct file attachments this previews.
- **`camera-photo-attach-low-memory.md`** — separate: large camera photos still OOM on mobile
  (the downscale falls back to the raw file on decode failure). Not addressed here.
