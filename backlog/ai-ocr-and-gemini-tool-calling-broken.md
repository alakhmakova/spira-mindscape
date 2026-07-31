# AI chat: image "OCR" fails on Mistral & Gemini; Gemini tool-calling / web-search broken

- **ID:** BUG-016
- **Status:** 🔧 In progress (2026-07-31) — safe Gemini fix + diagnostics applied; the OCR
  symptom is mitigated via direct attachments (BUG-017); root cause of Gemini tool-calling still
  needs one live run with a real key to confirm. See Progress.
- **Reported by:** User
- **Area:** Backend AI providers (`backend/.../ai/provider/{google,mistral}`), AI chat agentic
  loop (`ai/chat/AiChatService.java`), vision path (`ai/provider/VisionSupport.java`,
  `ai/chat/ResourceReadService.java`)
- **Severity:** High (two of four providers can't read images; Gemini can't use any tool —
  web search, URL read, resource read, or goal-change proposals)

## Summary

Two related problems reported from real use:

1. **Image "OCR" (vision) does not work on Mistral or Gemini.** Uploading an image (or a scanned
   PDF) and asking the assistant to read it returns nothing useful. On Anthropic it works;
   OpenAI is untested by the user so far.
2. **Gemini tool-calling appears broken in general.** Gemini "can't search the internet", and it
   looks like tool calling as a whole (`web_search`, `read_url`, `read_resource`,
   `propose_goal_change`) does not fire on Gemini.

These two are **linked**: the assistant only sees an image by *calling the `read_resource`
tool*, whose result carries the picture back (see `docs/vision-image-support-guide.md`). If a
provider can't call tools, it can never pull an image in — so "Gemini OCR" fails **because**
Gemini tool-calling fails. Mistral is a **separate** cause (model, not tools — see below).

## Steps to reproduce

**Gemini tool-calling:**
1. Save a Gemini (Google AI Studio) key under "Bring your own key"; pick `gemini-2.5-flash`.
2. Also save a Tavily key so `web_search` is offered.
3. In chat ask: "search the web for the current price of X" (or paste a URL and say "read this").
4. **Expected:** Gemini calls `web_search`/`read_url`, summarises with sources.
   **Actual:** it answers from its own knowledge / says it can't, never calling the tool.

**Mistral OCR:**
1. Save a Mistral key; keep the default model (`mistral-large-latest`).
2. In a goal, upload an image resource; ask "what's in this image?".
3. **Expected:** the assistant describes the picture.
   **Actual:** it can't see it.

## Root cause

### Mistral OCR — *confirmed by design*: non-vision model

The vision path is model-dependent (`docs/vision-image-support-guide.md` §2: "Sending an image
doesn't magically upgrade a text-only model"). Mistral's default here is **`mistral-large-latest`,
which is text-only** — only **Pixtral** models (`pixtral-12b-latest`, `pixtral-large-latest`) can
see images. So even when `read_resource` returns the image, `mistral-large` cannot read it. The
allow-list and wire-format in `VisionSupport.openAiImageUserMessage(...)` are correct; the model
is the problem. (Mistral also has a dedicated document-OCR product endpoint, separate from chat —
out of scope for the chat vision path.)

### Gemini tool-calling — *suspected* (needs live verification with a real key)

`GeminiProvider` targets Gemini's **OpenAI-compatibility layer** and its request/stream code is
near-identical to `OpenAiProvider` (which works). So the break is on the wire, not obvious from
the code. Leading hypotheses, to confirm by capturing a real Gemini response:

- **H1 — streamed tool calls carry no `id`.** Gemini's compat layer may emit `tool_calls` deltas
  without an `id`. `processStream` then builds a `ToolCall(null, name, args)`. A single
  `propose_goal_change` still surfaces, but any **looping** tool (`web_search`, `read_url`,
  `read_resource`) re-enters the agentic loop, where the follow-up request sends a `tool` message
  whose `tool_call_id` is `null` → Gemini rejects it → the turn errors out and nothing useful
  comes back. **This alone would break web search and OCR on Gemini.**
- **H2 — the tools JSON schema is rejected.** `propose_goal_change`'s schema is large (30+ enum
  values, a nested `items` array). Gemini's function-declaration subset is stricter than OpenAI's;
  if it 400s the whole request, tools never work.
- **H3 — arguments arrive as a JSON object, not a string chunk.** Handled already
  (`argNode.isTextual() ? … : argNode.toString()`), but worth confirming it accumulates to valid
  JSON.

There is currently **no diagnostic logging** in `GeminiProvider` (Mistral has a
`sawToolCalls/emitted` log line), so we're blind to which hypothesis holds.

## Fix approach

- **Gemini id fallback (H1):** synthesise a stable `id` per tool-call index at parse time when the
  provider supplies none, and reuse it consistently for the assistant echo **and** the
  `tool_call_id` of the result, so the agentic loop pairs correctly. Low-risk (only fills a gap).
- **Diagnostics:** add the same `sawToolCalls`/`emitted` logging to `GeminiProvider`, and on a
  non-200 keep logging the error body (already done). This lets the owner run one real request and
  tell us which hypothesis is true.
- **Schema (H2), if confirmed:** trim/relax the tool schema for Gemini (or send a reduced schema)
  until it accepts it.
- **Mistral OCR:** this is a **model** issue — surface a clear message when an image is sent to a
  model that returns a "can't view images" style error, and document that a **Pixtral** model is
  required for image reading on Mistral (see also the direct-attachment enhancement, BUG-017,
  which sends the image in the user turn and so removes the *tool-calling* dependency for OCR — but
  still needs a vision-capable model).

## How to verify fixed

1. With a real Gemini key: "search the web for …" → Gemini calls `web_search` and cites sources;
   paste a URL + "read this" → it calls `read_url`; "create a goal called X" → a proposal card
   appears. Backend log shows `sawToolCalls=true, emitted>=1`.
2. With a Gemini vision model: upload an image, "what's in this image?" → it describes the real
   picture (via `read_resource` **or** a direct attachment once BUG-017 lands).
3. With a Mistral **Pixtral** model: image reading works; with `mistral-large` the user gets a
   clear "this model can't view images — pick a Pixtral model" message rather than silence.
4. Provider unit tests (no network) assert the request-body/stream shapes for the above.

## Progress (2026-07-31)

**Confirmed by live testing (owner, real Gemini key):**
- Direct **file upload** in chat works on Gemini (image/PDF/DOCX) — as predicted, because the file
  rides in the user turn with no tool call.
- **Reading a resource** (`read_resource`) failed with: *"Function call is missing a
  thought_signature in functionCall parts … function call `default_api:read_resource`."* This is
  the real root cause of "Gemini tool-calling broken": **Gemini 2.5 attaches a `thought_signature`
  to each function call that must be echoed back verbatim on the follow-up turn** of a multi-turn
  (agentic) loop. We were rebuilding the tool-call echo from name+args only and dropping it, so
  Gemini rejected the second turn. `propose_goal_change` doesn't loop, so it wasn't affected.

**Fix applied (2026-07-31):**
- `ai/provider/ToolCall.java` — new optional `extraContentJson` field (provider echo metadata;
  null for Anthropic/OpenAI/Mistral).
- `ai/provider/google/GeminiProvider.java` — capture the tool call's `extra_content` (which holds
  the `thought_signature`) during streaming and **echo it back verbatim** on the assistant
  tool-call in the follow-up request. Added a `withThoughtSignature` count to the finished-log and
  a one-shot WARN dump of the raw tool-call chunk *if* no signature is found (so the exact wire
  location is visible should Google change it).
- Test: `GeminiThoughtSignatureTest` asserts the signature is replayed (and omitted when absent).
- **Needs owner re-test** with a real key: read a resource on Gemini → no thought_signature error;
  log shows `withThoughtSignature>=1`. If the WARN dump fires instead, paste it here and we adjust
  where we read the signature from.

**Earlier (safe part, still in place)**
- `ai/provider/google/GeminiProvider.java` — **tool-call `id` fallback**: when Gemini's stream
  omits an id, synthesize `gemini_call_<index>` and reuse it for both the assistant echo and the
  `tool_result`, so the agentic loop (web_search / read_url / read_resource) no longer sends a
  `null` `tool_call_id` (addresses hypothesis **H1**). Added `sawToolCalls`/`emitted` diagnostic
  logging (mirrors Mistral) so one real request reveals what Gemini actually returns.
- **OCR symptom mitigated** by BUG-017: a directly-attached image rides in the user turn, so the
  model reads it **without** any tool call — image OCR now works on Gemini regardless of the
  tool-calling issue. (A vision model is still required — so **Pixtral** on Mistral.)

**Still open (needs the owner's real keys — the agent has none)**
- Confirm with logs whether Gemini now emits/executes tools (`sawToolCalls=true, emitted>=1`), i.e.
  whether H1 was the whole story or H2 (schema rejection) also bites. If H2, reduce the tool schema
  for Gemini.
- Mistral: surface a clearer "this model can't view images — pick a Pixtral model" message on the
  vision-not-supported error, and document the Pixtral requirement.

## Resolution

_(fill in once the live Gemini run confirms tools fire and the Mistral message is added — files
changed; the user commits manually.)_
