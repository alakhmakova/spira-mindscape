# AI chat: attach PDF / image / docx directly to a message (no forced Resource save)

- **ID:** BUG-017
- **Status:** ✅ Fixed (2026-07-31) — implemented + unit-tested; one manual real-model check
  remains (see Resolution). Pending manual commit by the user.
- **Reported by:** User
- **Area:** AI chat — frontend (`src/components/ai/AiPanel.tsx`, `ai-api.ts`) and backend
  (`ai/chat/dto/ChatRequest.java`, `ai/chat/AiChatService.java`, `ai/provider/*`,
  `ai/chat/ResourceTextExtractor.java`)
- **Type:** Enhancement (not a defect)

## Summary

Today the only way to get a file in front of the assistant is to **save it as a Resource** on a
goal and let the model call `read_resource`. The user wants to **attach a PDF, image, or DOCX
directly to a chat message** — a one-off, without being forced to persist it as a Resource.
**Resources stay** exactly as they are; direct attachment is an **additional**, lighter path
(like attaching a file in ChatGPT/Claude).

Bonus: attaching an **image directly** puts it in the *user turn* itself, so the model sees it
**without calling a tool** — which side-steps the Gemini tool-calling problem (BUG-016) for the
OCR case. (It still needs a **vision-capable** model to actually read an image.)

## Desired behaviour

- A paperclip/attach control in the chat composer (web first; Android later for parity) accepts
  **images** (png/jpeg/webp/gif), **PDF**, and **DOCX**.
- Attachments show as small chips above the input, removable before sending.
- On send, each attachment travels with the message (base64 data URL), scoped to **that message
  only** — not written to the goal's Resources.
- Backend turns each attachment into model input:
  - **image** → an image part in the user message (reuse `VisionSupport` / `LlmImage`), for
    vision models;
  - **PDF** → extracted text via the existing `ResourceTextExtractor` (scanned/no-text PDFs get
    the same "no extractable text" note);
  - **DOCX** → extracted text (new: Apache POI `XWPFDocument`, or unzip `word/document.xml`).
- Untrusted-content fencing applies to extracted text, same as tool results.
- Sensible size cap (match the existing resource upload cap, ~5 MB) enforced client- and
  server-side; oversize is rejected with a clear message.

## Decisions taken

- **DOCX:** supported **now**, with **no new dependency** — a `.docx` is a ZIP, so
  `DocxTextExtractor` unzips `word/document.xml` and reads the `<w:t>`/tab/paragraph markers.
- **Persistence:** attachments are **ephemeral** — sent with their one message, never written to
  Resources.

## How to verify (once built)

- Attach an image + ask about it (vision model) → described, nothing added to Resources.
- Attach a text PDF → summarised; a scanned PDF → "no extractable text" message.
- Attach a DOCX → its text is used.
- Oversize file → rejected with a readable message, no crash.
- Unit tests: `ChatRequest` carries attachments; each provider serialises an attached image into
  its wire shape; PDF/DOCX text extraction; the security boundary (attachment text is fenced as
  untrusted).

## Resolution

Built end-to-end (web + all four providers), unit-tested. Files changed:

**Backend**
- `ai/chat/dto/ChatRequest.java` — new `attachments` list of `Attachment(name, mime, dataUrl)`,
  `@Valid`, count capped at 6, each `dataUrl` ≤ 7.5 MB; a 7-arg convenience constructor keeps
  existing callers/tests working.
- `ai/chat/DocxTextExtractor.java` (new) — dependency-free DOCX → text (unzip + `<w:t>`/tab/`</w:p>`
  markers), bounded output.
- `ai/chat/AiChatService.java` — `buildUserMessage(...)` folds attachments into the user turn:
  images ride as `LlmImage` vision blocks; PDF (existing `ResourceTextExtractor`) and DOCX are
  text-extracted and appended under an "[Attached file: …]" heading, **fenced as untrusted
  content**. Added an "ATTACHED FILES" note to the chat system prompt.
- `ai/provider/anthropic/AnthropicProvider.java` — render image(s) inline on a plain user message
  (previously only tool-result images were handled). OpenAI/Mistral/Gemini already appended a
  follow-up image message for any message carrying images, so they needed no change.
- `ai/provider/VisionSupport.java` — neutral wording on the image note (fits attachment or
  resource).

**Frontend**
- `components/ai/ai-api.ts` — `ChatAttachment` type; `streamChat` sends `attachments` in the body.
- `components/ai/AiPanel.tsx` — composer paperclip + hidden file input (images/PDF/DOCX, ≤ 5 MB,
  ≤ 6 files), removable chips, validation messages; attachments flow through `sendChat` and render
  as chips on the user bubble. Attach shown in regular chat only (`allowAttachments={!inGrow}`).

**Tests** — `DocxTextExtractorTest`, new user-message-image cases in the Anthropic & OpenAI vision
tests, and `ai-api.test.ts` cases asserting attachments are (and aren't) sent. All backend AI
tests (125) and frontend tests (117) pass; `tsc`/`eslint` clean.

**Note (bonus for BUG-016):** because an attached image now rides in the *user turn*, the model can
read it **without calling `read_resource`** — so image OCR works on Gemini even while its
tool-calling issue is open. It still needs a **vision-capable** model (so a Pixtral model on
Mistral).

**Remaining manual check (owner):** with a real vision key, attach an image and a PDF/DOCX in chat
and confirm the model uses them and nothing is added to Resources.
