# Personal Tools — Sandboxed AI-Written Renderers

**Status: IMPLEMENTED (phases 1–2).** The AI can now write *actual rendering
code* for a tool, and that code runs inside a locked browser sandbox where it
cannot touch the user's session, data, or the rest of the app. This is the safe
answer to "why can't the AI just write the UI like Claude does in artifacts?".

This doc is both the **design rationale** and a **beginner-friendly guide** you
could follow to reproduce the same isolation in another project. For the
security *tests* that prove it works, see [`playwright-e2e.md`](./playwright-e2e.md).

---

## 0. Plain-English primer (read this first if the terms are new)

- **iframe** — a web page embedded inside another page (a "frame"). We render the
  AI's tool code inside its own iframe so it's separated from the main app.
- **Origin** — the `scheme://host:port` a page belongs to (e.g.
  `https://app.example.com`). The browser's #1 security rule (the *same-origin
  policy*) is: code from one origin cannot read another origin's cookies, DOM,
  or storage.
- **`sandbox` attribute** — an iframe option that *removes* capabilities. We give
  it `allow-scripts` (so the AI code can run) but **not** `allow-same-origin`.
  Without `allow-same-origin` the iframe is forced onto a unique **"opaque"
  origin** — it belongs to *nobody*, so the same-origin policy blocks it from the
  app's cookies/DOM/storage.
- **CSP (Content-Security-Policy)** — a per-page rule list saying what the page
  may load/do. We set `default-src 'none'` inside the iframe = "no network at
  all".
- **`postMessage`** — the *only* sanctioned way two frames on different origins
  talk: one side sends a message object, the other listens for it. It's a
  message slot, not shared memory — no direct function calls or DOM access.
- **Why this is safe**: the AI's code is *untrusted* (a model can be wrong or be
  tricked by a user's words — "prompt injection"). So we run it like we'd run a
  stranger's code: in a box with no keys, no phone line, and a single mail slot.

---

## 1. Why this is hard (and why Claude artifacts can do it)

Spira is a multi-user, server-backed app holding private data and a session
cookie. Running **arbitrary AI-generated code inside the app's own origin** =
code execution in the user's authenticated context: it could read the session,
call our API as the user, reach other users' data, or send data to an attacker.
The model's output is untrusted and can be steered by prompt injection, so this
is not acceptable.

Claude artifacts can run AI code because they execute in an **isolated, opaque
sandbox in the user's own browser** with no access to the real session, backend,
or parent page — a throwaway, single-user context. The same isolation makes it
acceptable here. The work is *building that isolation correctly*, not "letting
the model write code into our bundle".

---

## 2. The isolation model (exactly as built)

1. **Opaque-origin sandboxed iframe.** The tool renders in
   `<iframe sandbox="allow-scripts" srcdoc={…}>` — **no `allow-same-origin`**. The
   frame gets a unique opaque origin: no parent cookies, `localStorage`, or DOM.
   ⚠️ **Never combine `allow-scripts` with `allow-same-origin`** — together they
   let the frame escape. (The e2e test `…WITHOUT allow-same-origin` fails loudly
   if anyone ever adds it.)
   *Code:* `buildSrcdoc()` in `src/components/tools/sandbox/runtime.ts`; the
   `<iframe>` is mounted in `src/components/tools/sandbox/ToolSandbox.tsx`.

2. **No network.** A CSP inside the frame denies all network/storage:
   ```
   default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline' 'unsafe-eval'; img-src data:;
   ```
   - `default-src 'none'` → no `fetch`, no `<img src=http…>`, nothing leaves.
   - `script-src 'unsafe-inline' 'unsafe-eval'` → needed so our trusted bootstrap
     can run, and so it can compile the AI module with `new Function(...)`.
     **Note vs. the original sketch:** we DO include `'unsafe-eval'`. That sounds
     scary but is fine here: the frame has an opaque origin and *no network*, so
     "being able to run code" is the whole point and is fully contained — eval
     can't reach cookies, the API, or the parent.
   - `style-src 'unsafe-inline'` lets the module style its output; `img-src data:`
     allows inline data-URL images only.

3. **Data only via `postMessage`.** The parent passes the tool's schema + records
   into the frame; the frame renders. On add/edit/delete the frame **posts a
   request back**, and the **parent** validates it (the existing
   `ToolRecordValidator`, ownership-checked) and calls the real API. The frame
   never touches the backend — the write-path security boundary is unchanged.
   *Code:* protocol in `src/components/tools/sandbox/protocol.ts`; the parent
   applies mutations in `ToolSandbox.tsx` (`applyMutate`).

4. **The runtime API given to the AI module (actual shape).** The AI writes a
   **function body** that receives one argument, `ctx`:
   ```js
   ctx.root       // a <div> to fill with your UI
   ctx.schema     // the tool's schema {layout, columns:[…]}
   ctx.records    // current rows: [{ id, data }]
   ctx.theme      // "light" | "dark" (so the module can match the app)
   ctx.addRow(data)            // request to add a row
   ctx.editRow(id, data)       // request to change a row
   ctx.deleteRow(id)           // request to delete a row
   ```
   That's all it gets — no `fetch`, storage, imports, or parent access.
   *Code:* the `ctx` object is built in the bootstrap inside `runtime.ts`; the
   reference implementation the AI mimics is
   `src/components/tools/sandbox/default-render.ts`.

5. **Resize, errors, theme & a hang watchdog.**
   - The frame reports its content height (`resize`, throttled to one per
     animation frame) so the parent sizes the iframe.
   - Render exceptions are posted as `error` and shown as a toast.
   - The parent passes the app `theme` in `init`; the frame sets `color-scheme`
     and a few CSS variables so a module can match light/dark.
   - **Hang watchdog:** after a successful draw the frame posts `rendered`. If
     the parent gets neither `rendered` nor `error` within ~6 s it assumes the
     module hung (e.g. an infinite loop), tears the iframe down, and shows a
     fallback message. *Honest limitation:* a purely synchronous infinite loop
     can only be cleaned up if the browser process-isolates the frame (Chrome
     does for sandboxed iframes; the watchdog fires and the main app stays
     responsive). This is an **availability** concern only — confidentiality
     (no data leak) holds regardless, since the frame still has no
     network/cookies/parent access. *Code:* `WATCHDOG_MS` in `ToolSandbox.tsx`.

### Residual risks
- A hostile/buggy module **cannot** exfiltrate (no creds/network/origin) or
  affect the rest of the app — it's contained to its frame. That containment is
  exactly what makes "AI writes code" tolerable.
- The dominant risk is **misconfiguration** (a stray `allow-same-origin`). Hence
  the hardened single host (`buildSrcdoc`), the CSP, and the Playwright security
  tests asserting the frame can't read `document.cookie`, call the network, use
  storage, or reach `window.parent`.

---

## 3. Data model & API (as built)

- `tool_definitions.render_code TEXT` (nullable) — migration
  `V18__tool_render_code.sql`. Null = schema-rendered (the default); non-null =
  rendered by this code in the sandbox. The **schema stays** — it still
  types/validates the records.
- `render_code` is inert TEXT — **never executed in the parent**, only inside the
  sandbox. Size-capped at 64 KB (`ToolService.MAX_RENDER_CODE_BYTES`).
- Records are still written via `POST/PATCH/DELETE /api/tools/{id}/records` from
  the parent, validated server-side (`ToolRecordValidator`). No new write surface.

---

## 4. AI tool integration (as built)

- `propose_tool` and `edit_tool` (in `AiChatService`) gained an optional
  **`render`** string: the function body described in §2.4. The system prompt
  tells the model when to use it (only for bespoke looks the table/fields layouts
  + display options can't express) and that it runs in the locked sandbox.
- The proposal flows over SSE like other proposals; the approval card previews
  the tool **inside the sandbox** (`ToolSandbox preview`) so the user sees the
  real thing before accepting.
- On accept, `render` is stored via `createTool`/`updateTool` (an empty string on
  edit clears it, back to schema-rendered).

---

## 5. File map (where everything lives)

| Concern | File |
|---|---|
| Message protocol + validator | `src/components/tools/sandbox/protocol.ts` |
| Sandboxed iframe HTML + bootstrap | `src/components/tools/sandbox/runtime.ts` |
| Default render module (reference) | `src/components/tools/sandbox/default-render.ts` |
| Parent component (mounts iframe, bridges data) | `src/components/tools/sandbox/ToolSandbox.tsx` |
| Picks sandbox vs schema renderer | `src/components/tools/ToolWindows.tsx` |
| AI proposal preview in sandbox | `src/components/ai/AiPanel.tsx` (`ToolProposalCard`) |
| API client (`renderCode` on Tool) | `src/lib/spira/tools-api.ts` |
| DB column | `backend/.../db/migration/V18__tool_render_code.sql` |
| Entity / DTO / service | `backend/.../tools/ToolDefinition.java`, `dto/ToolDtos.java`, `ToolService.java` |
| AI tool specs + prompt | `backend/.../ai/chat/AiChatService.java` |
| Browser security tests (isolation) | `e2e/sandbox-isolation.spec.ts` (see `playwright-e2e.md`) |
| Unit tests (protocol + parent round-trip) | `src/components/tools/sandbox/protocol.test.ts`, `ToolSandbox.test.tsx` |

---

## 6. How to reproduce this isolation in ANOTHER project

The pattern is framework-agnostic. Minimum viable version:

1. **Make the host string.** A function returning an HTML document with:
   - the CSP meta tag from §2.2,
   - a small **trusted** `<script>` (the "bootstrap") that: listens for a
     `postMessage`, on `init` does `const render = new Function("ctx", code)`,
     builds a `ctx` with your data + callbacks, calls `render(ctx)`, and posts
     back any data-change requests. (Copy `runtime.ts`.)
2. **Mount the iframe** with `sandbox="allow-scripts"` and `srcdoc={thatHtml}` —
   **never** add `allow-same-origin`. (Copy the `<iframe>` in `ToolSandbox.tsx`.)
3. **Define a tiny message protocol** and validate every inbound message shape
   (don't trust the frame's output). (Copy `protocol.ts`.)
4. **Keep writes on the parent.** The frame only *requests* changes; the parent
   validates + persists. Never give the frame a token or a direct API path.
5. **Write the browser security tests** before trusting it — assert the frame
   can't read cookies, use the network, use storage, or reach the parent. (Copy
   `e2e/sandbox-isolation.spec.ts`; guide in `playwright-e2e.md`.)

Gotchas a beginner will hit:
- Forgetting that **`new Function`/eval needs `'unsafe-eval'`** in the CSP — the
  module silently won't run otherwise.
- Adding `allow-same-origin` "to make something work" — that **breaks the whole
  sandbox**. If you need it, you've designed something wrong.
- `postMessage` targetOrigin: to talk *to* an opaque-origin frame you must use
  `"*"` (it has no nameable origin). That's fine because you only ever post to
  *your own* iframe handle.

---

## 7. What's left / next ideas (optional)

Robustness gaps from the first review are now closed: resize is throttled, a
hang watchdog tears down unresponsive modules, the theme is propagated, and the
parent data round-trip (frame → server-validated API → records back) is covered
by `ToolSandbox.test.tsx`. Remaining nice-to-haves:

- A small library of AI render examples (board/kanban, calendar grid) to steer
  the model toward good, self-contained modules.
- A "report" channel so a render error is shown in-place inside the frame, not
  just as a toast.
- An e2e test that also exercises the data round-trip against a (mocked) API,
  not just the isolation.
