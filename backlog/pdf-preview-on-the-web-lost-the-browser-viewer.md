# BUG-087 — The web PDF preview lost the browser's viewer: zoom is all that is left

**Status:** ✅ Fixed (2026-09-15, awaiting the owner's live check)
**Area:** Resources · Web
**Severity:** Medium — reading a CV or a certificate lost search, text selection, thumbnails and print

## Summary

Until July 2026 a PDF resource opened in the browser's own PDF viewer. It now renders page images
with a single zoom control and nothing else (owner, 2026-09-15).

## Steps to reproduce (before the fix)

1. On a laptop, open a goal with a PDF resource.
2. Open its preview: pages render, but there is no search, no text selection, no thumbnails, no print.

## Root cause

Commit `ec5f272` (2026-07-25, "pdf visibility on the web fix") replaced the iframe-on-blob-URL
viewer with a PDF.js canvas renderer (`PdfViewer.tsx`) for every screen size. It fixed blank
previews on phones, whose browsers do not embed PDFs in iframes, and removed the native viewer on
desktops where it had worked.

## Fix

`PdfViewer` chooses by width (`useIsMobile`): the browser's native viewer on an iframe blob URL on
laptops and tablets, the PDF.js canvas on phones. The CSP already allows `frame-src blob:`.

## How to verify

- `e2e/pdf.spec.ts`: a 1280px viewport gets an `iframe` on a `blob:` URL; a 390px viewport gets a canvas.
- Live: on a laptop, the preview has the browser's toolbar (search, print, thumbnails).

## Resolution

Fixed as described.
