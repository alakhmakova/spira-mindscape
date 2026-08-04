# Mistral invents the contents of an attached image instead of admitting it can't read it

- **ID:** BUG-027
- **Status:** ✅ Fixed (2026-08-03)
- **Reported by:** User (2026-08-03)
- **Area:** Backend AI (`ai/chat/AiChatService.java` — system prompt + attachment path,
  `ai/provider/VisionSupport.java`, `ai/provider/mistral/MistralProvider.java`), web chat
  attachments (`src/components/ai/AiPanel.tsx`)
- **Severity:** High (a confident, fabricated answer is worse than no answer — the user cannot
  tell it apart from a real reading)

## Summary

Attaching a photo of a **handwritten note** to the chat while the Mistral provider is selected
returns a fluent, confident answer about the note's contents — which the model never read. It does
not say "I can't see this" or "I can't make out the handwriting"; it **pretends**.

The reported example is a photo of a lined-paper note in mixed Russian / Swedish / English cursive
("Rågen Roast'n toast 263 kkal/100…", "Grilljanga fläsksida 270 kkal/100 g", "Melon 60 грамм",
"ICA Selection"), photographed sideways on a dark table.

Related but distinct: **BUG-016** records that Mistral (and Gemini) cannot read images at all.
This entry is about the *behaviour when that happens* — silent fabrication — and about making
handwriting actually readable rather than merely admitting defeat.

## Steps to reproduce

1. Save a Mistral key, keep the default model (`mistral-large-latest`).
2. Open the chat, attach a photo of handwritten text (the paperclip in the composer).
3. Ask "what is written here?".
4. **Expected:** either the actual text, or a plain "I can't read this image — please type it".
   **Actual:** an invented, plausible-sounding transcript.

## Root cause (three layers, all real)

1. **The selected model is text-only.** `mistral-large-latest` has no vision; only Pixtral
   (`pixtral-large-latest`, `pixtral-12b-latest`) and the newer multimodal Mistral models can see
   an image. Confirmed already in BUG-016.
2. **Nothing checks that.** `AiChatService.buildUserMessage` (~line 1086) attaches every
   vision-MIME file as an `image_url` part via `VisionSupport`, whatever model is selected — there
   is no vision-capability allow-list anywhere in the backend or the model picker. The image is
   dropped on the provider's side while the *text* of the turn still says
   `[Attached image: photo.jpg]`, so the model knows an image was meant to be there and answers as
   if it had seen it.
3. **The prompt permits the guess.** The system prompt says "describe what you actually see"
   (READING RESOURCES / ATTACHED FILES) but never says *what to do when no image reached you, or
   when the handwriting is illegible* — unlike the scanned-PDF case, which does say "never invent
   its contents".

Beyond that, this particular image is at the hard end for **any** model: joined-up cursive, three
languages, ruled paper, taken at an angle. Two client-side details make it harder than it needs to
be — the composer downscales photos to `IMAGE_MAX_DIM = 1600` px (`AiPanel.tsx:5195`), which costs
stroke detail, and `createImageBitmap` is called without an explicit
`imageOrientation: "from-image"`, so a sideways phone photo may reach the model rotated.

## Fix

1. **A capability guard, so nothing is fabricated.**
   `VisionSupport.modelCanSeeImages(provider, model)` — an **allow-list** for Mistral (pixtral /
   mistral-medium / mistral-small / magistral; everything else, including the default, is blind)
   and a deny-list for the providers whose chat line-ups are multimodal throughout. A model that
   can't see is no longer sent the picture at all: it gets the OCR text, or an explicit note that
   it was shown nothing, naming the model and telling it to say so rather than guess.
2. **Mistral's own OCR model does the reading.** New `MistralOcrService`
   (`mistral-ocr-latest`, `POST /v1/ocr`) turns an image — or a scanned, text-less PDF — into
   Markdown, which is fed into the turn as an `[Attached file: …]` untrusted block, the same path
   `ResourceTextExtractor` / `DocxTextExtractor` already use. It runs when the chat model is blind
   (any provider, if a Mistral key is saved) and whenever the provider is Mistral — its vision
   models transcribe handwriting poorly even when they can see. Vision models still get the
   picture **and** the OCR text. Failure is never fatal: no text → the honest note.
   The text is labelled as a machine reading, so the model reports uncertainty instead of "I see…".
3. **The prompt now forbids the guess.** Both the resource path and the attached-file path say:
   if no picture and no text reached you, or the handwriting is illegible, say so and ask the user
   to type it; read what you can and name what you could not; a confident invention is the worst
   possible answer.
4. **A better picture reaches it.** `createImageBitmap` is called with an explicit
   `imageOrientation: "from-image"`, so a sideways phone photo is uprighted before it is scaled
   (browsers differ on the default). Because resizing happens *after* that rotation, only one axis
   is constrained now — passing both, computed from the pre-rotation header dimensions, would have
   squashed a quarter-turned photo.

Deliberately not done: raising `IMAGE_MAX_DIM` above 1600 px for OCR-bound images. More pixels
would help the OCR — but the cap was introduced against the mobile attachment crash
(`camera-photo-attach-low-memory.md`, BUG-022), and whether it actually helps there is **still
unproven**: that bug's own root cause is marked "suspected", and the crash recurs. Changing the cap
without settling BUG-022 first would be trading a real OCR gain against an unknown. The evidence
comes from `specs/2026-08-03-attachment-crash-diagnostics/`.

Honest expectation: multi-language cursive on ruled paper still comes back partial. The goal is a
*truthful* partial reading the user can correct — never a confident invention.

## How to verify fixed

1. With `mistral-large-latest` selected, attach the sample photo → the assistant says it cannot
   see images with this model and names a vision model; it does **not** produce a transcript.
2. With OCR routing on, attach the same photo → the answer contains real fragments from the note
   ("263 kkal", "Melon", "ICA Selection", "fläsksida") and openly flags what it could not read.
3. Attach a photo of clean printed text → transcribed accurately.
4. Attach a sideways photo → the reading is no worse than the same photo rotated upright.
5. A scanned (image-only) PDF is no longer answered with "ask the user to paste the text" when OCR
   can read it.

## Resolution

Fixed on 2026-08-03.

- `ai/provider/VisionSupport.java` — `modelCanSeeImages`.
- `ai/provider/mistral/MistralOcrService.java` — new.
- `ai/chat/AiChatService.java` — `VisionContext`, the attachment and `read_resource` image paths,
  scanned-PDF fallback, and the two prompt sections.
- `src/components/ai/AiPanel.tsx` — `imageOrientation: "from-image"` + single-axis resize.

Tests: `VisionSupportTest` (the allow-list, including the blind default), `MistralOcrServiceTest`
(response parsing, caps, refusal without a key), `AiChatServiceImageAttachmentTest` (blind model
gets OCR text and never the image; without OCR it is told it was shown nothing; a vision model
keeps the picture; the Anthropic path is untouched and makes no OCR call). Full backend suite:
808 passing.

Verified live with the reported photo and `mistral-large-latest` selected: the answer now contains
the note's real content ("Pogen Roast'n toast 263 kkal/100", "Griljerad fläsksida 270 kkal/100 g",
"Mjölk", "Melon 60", "ICA Selection") and lists the fragments it could not make out, instead of an
invented transcript.
