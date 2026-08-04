# Requirements: attachment crash diagnostics

Instrumentation to find out — with evidence rather than reasoning — **why attaching a camera photo
kills the mobile browser tab** (BUG-022). Nothing here changes product behaviour; it exists to make
one intermittent, unobservable failure observable.

Status: **specified, not built** (2026-08-03).

## Why this is needed

The reported symptom is that the page **dies and reloads**, with a system-level toast from the
phone. That single fact rules out most of what we can currently see:

- **No JavaScript runs.** A renderer killed by the OS never reaches a `catch`, a `finally`, or an
  `onerror`. Every message the app can produce — `Couldn't read "IMG_1234.jpg". Try again.`,
  `Image too large to process on this device`, `That image is too large.` — belongs to a
  *different*, survivable failure, not to this one.
- **The server sees nothing.** The tab dies before the request is sent, so backend logs and
  provider errors are irrelevant to this crash. (A separate suspicion that it is Gemini-specific
  therefore needs its own evidence — if it is real, the crash is happening during a long streamed
  answer, not during attachment, and the timeline below will show that.)
- **In-memory state is gone.** Anything not written to disk *before* the crash is unrecoverable.

So the current fix for BUG-022 — decode-at-reduced-size, `IMAGE_MAX_DIM = 1600` — is an
**unverified hypothesis**. It may be right, partly right, or beside the point: on Android the
camera app is heavy enough that the low-memory killer can evict a background browser tab *while the
user is taking the photo*, i.e. before any of our code runs. Those two causes are indistinguishable
today, and they need opposite fixes.

## What must be answered

1. Did the tab die **during the camera excursion** (before our code ran) or **inside our decode**?
2. If inside: at which step — `createImageBitmap`, the canvas draw, `toDataURL`, or state/storage?
3. How much memory was in use at each step, and how big was the input (bytes and pixels)?
4. Does it also happen with a gallery pick, or only with a capture?
5. Is there any correlation with the selected provider (the Gemini suspicion) or with a streaming
   answer being in flight?

## 1. A crash-surviving breadcrumb trail

The only thing that outlives the crash is what was written synchronously to `localStorage`.

- One trail per attachment attempt, under a single key (e.g. `spira:attach-trace`), appended
  **synchronously** at every step, capped (last ~50 entries) so it can't grow without bound.
- Each entry: monotonic timestamp, step name, and the step's facts —

  | Step | Recorded |
  |---|---|
  | `picker-open` | how the picker was opened (attach button), current provider |
  | `hidden` / `visible` | `visibilitychange` — this is what proves a camera excursion |
  | `file-picked` | name, MIME, byte size, count |
  | `header-read` | pixel dimensions from the PNG IHDR / JPEG SOF (`readImageSize`) |
  | `decode-start` / `decode-done` | which path (scaled decode vs full), elapsed ms, resulting bitmap size |
  | `canvas-done` | output pixels, data-URL length, elapsed ms |
  | `attached` | final size in state |
  | `send` | request payload size, provider |
  | `stream-start` / `stream-done` | for the Gemini question: was an answer streaming when it died |

- Every entry also carries `performance.memory.usedJSHeapSize` / `jsHeapSizeLimit` where the
  browser exposes it (Chrome does; note it in the entry when unavailable) plus, once per trail,
  `navigator.deviceMemory` and `navigator.userAgent`.
- **The interpretation is the point**: after a crash-and-reload, the last entry is where it died.
  *No entry at all, or a trail ending at `hidden`, means the tab was evicted during the camera —
  and no decode limit of ours could have prevented it.*

## 2. Report it after the reload

The phone's console is not reachable, so the trail has to come to us.

- On load, if a trail exists and its last entry is not a terminal one (`attached`, `send`,
  `stream-done`), treat it as a crash record.
- POST it to a small backend endpoint that only logs it (no storage, no new table); log it at
  `WARN` with a clear marker so it is greppable. Nothing but sizes, timings, step names and UA —
  **never the image bytes, the file name's content, or any chat text**.
- Also surface it in the app for the owner: a plain "last crash report" view (a debug entry in the
  key/provider sheet is enough) that can be read and copied on the phone itself, so a diagnosis
  doesn't depend on the backend being reachable.
- Clear the trail once reported.

## 3. Stop swallowing the real error

`Composer.addFiles` (`src/components/ai/AiPanel.tsx`) currently turns **every** failure into
`Couldn't read "<name>". Try again.` — the actual `error.name` / `error.message` is discarded. This
is the survivable sibling of the crash and it is being made invisible for no reason.

- Keep the friendly message, but record the real error (name, message, file size, dims) in the
  breadcrumb trail, and show the technical reason where it helps the owner (the debug view above).

## 4. Independent confirmation from the device

The breadcrumbs say where our code was; `adb logcat` says what the OS did. They must agree.

- With the phone connected over USB and debugging enabled, capture `logcat` while reproducing:
  a low-memory kill appears as `lowmemorykiller` / `am_kill` / `Renderer process crashed`, naming
  the killed process.
- `chrome://crashes` on the phone gives the same event from the browser's side.
- Neither can be automated by the agent without the physical device — this step is the owner's,
  or the owner's plus a supervised `adb` session.

## 5. The three no-code experiments (do these first)

Cheap, and each one alone can settle question 1:

| Experiment | Crash still happens → | No crash → |
|---|---|---|
| The same photo picked from the **gallery** | our decode path is implicated | the camera excursion is the trigger |
| Photograph in the **camera app first**, then attach the saved file | our decode path | the browser tab is being evicted while the camera runs |
| Attach with the AI panel's answer **not** streaming, and with each provider in turn | narrows or kills the Gemini correlation | — |

## Definition of done

- A reproduction on a real phone produces a stored trail whose last entry names the step.
- That trail is readable both on the phone and in the backend log.
- BUG-022's "Root cause" section is rewritten from *suspected* to *established*, and the fix (or
  the removal of a fix that turns out to be irrelevant, e.g. the `IMAGE_MAX_DIM` cap) follows from
  the evidence.
- The instrumentation stays in the code, behind the same debug view — it costs nothing when no
  crash happens and it is what makes the next intermittent report answerable.

## Related

- `backlog/camera-photo-attach-low-memory.md` (BUG-022) — the bug this exists to settle.
- `backlog/mistral-invents-text-instead-of-reading-images.md` (BUG-027) — its "not done" note
  defers raising `IMAGE_MAX_DIM` for OCR quality; that decision waits on this evidence, since the
  cap's value against the crash is exactly what is unproven.
- `backlog/web-frontend-no-error-tracking.md` — the general gap this is a narrow, targeted case of.
