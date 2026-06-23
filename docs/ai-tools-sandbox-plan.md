# Personal Tools — Sandboxed AI-Written Renderers (Phase 2)

**Status: design only — not implemented.** This is the blueprint for letting the
AI write *actual rendering code* for a tool (artifact-style), instead of only
composing the fixed primitive catalog. It is the safe answer to "why can't the
AI just write the UI like Claude does in artifacts?".

It is a deliberate, security-sensitive project — separate from the incremental
["expand the builder"](./ai-mini-apps-plan.md) work (declarative display options
on the existing schema). Do this only when the declarative options provably
can't express what users need (genuinely bespoke widgets).

---

## 1. Why this is hard (and why artifacts can do it)

Spira is a multi-user, server-backed app holding private data and a session
cookie. Running **arbitrary AI-generated code inside the app's own origin** =
remote code execution in the user's authenticated context: it could read the
session, call our API as the user, reach other users' data, or exfiltrate over
the network. The model's output is untrusted and can be steered by prompt
injection, so this is not acceptable.

Claude artifacts can run AI code because they execute in an **isolated, opaque
sandbox in the user's own browser** with no access to the real session, backend,
or parent page — a throwaway, single-user context. The same isolation makes it
acceptable here. The work is building that isolation correctly, not "letting the
model write JSX into our bundle".

---

## 2. The isolation model

1. **Opaque-origin sandboxed iframe.** Render the tool in
   `<iframe sandbox="allow-scripts">` **without** `allow-same-origin`. The frame
   gets a unique opaque origin: no access to parent cookies, `localStorage`, or
   DOM. **Never combine `allow-scripts` with `allow-same-origin`** — together
   they allow a sandbox escape.
2. **No ambient credentials / no network.** With an opaque origin the session
   cookie is never attached and CORS blocks calls to our API. A CSP inside the
   frame (`default-src 'none'`; `script-src 'unsafe-inline'` only to run the
   generated module) denies all network/storage. So even hostile code has
   nowhere to send data.
3. **Data only via `postMessage`.** The parent passes the tool's schema + records
   into the frame (structured clone). The frame renders. On add/edit/delete the
   frame posts a request back; the **parent** validates it (the existing
   `ToolRecordValidator`, ownership-checked) and calls the real API. The frame
   never touches the backend directly — the write-path security boundary is
   unchanged.
4. **Narrow runtime API.** The generated module receives `{ data, onChange,
   onAddRow, onEditRow, onDeleteRow }` and returns DOM/markup. No `fetch`, no
   storage, no parent access — only these callbacks.
5. **Resource guards.** Heavy logic in a Web Worker with a timeout; modals
   disabled (no `allow-modals`); output height bounded. A broken artifact can
   only break its own frame.

### Residual risks
- A hostile/buggy artifact **cannot** exfiltrate (no creds/network/origin) or
  affect the rest of the app — it is contained to its frame. This containment is
  exactly what makes "AI writes code" tolerable.
- The dominant risk is **misconfiguration** (a stray `allow-same-origin`). Hence:
  a single hardened host page, a CSP, and a security review with explicit tests
  asserting the frame cannot reach `document.cookie`, the API, or the parent.

---

## 3. Data model & API changes

- `tool_definitions`: add `render_code TEXT` (nullable). A tool is either
  *schema-rendered* (today's path) or *code-rendered* (this path). Keep the
  schema too — it still types/validates the records.
- Store `render_code` as inert text (like the schema); **never executed in the
  parent**, only inside the sandbox.
- Writes still go through `POST/PATCH/DELETE /api/tools/{id}/records` from the
  parent, validated server-side. No new write surface.

---

## 4. AI tool change

- New/extended LLM tool: the model may emit a `render` module (a constrained,
  self-contained function using only the runtime API) in addition to the schema.
- The model is told: the module runs sandboxed, has no network/storage/session,
  and must use the provided callbacks for all data changes.
- Server stores the code after size/shape checks; it is surfaced in a preview
  (rendered inside the sandbox) for the user to approve, like other proposals.

---

## 5. Build checklist

1. Sandbox host document + iframe harness (separate origin or `srcdoc` with no
   `allow-same-origin`) + CSP.
2. `postMessage` protocol: `init(schema, records)` → frame; `mutate(op, …)` →
   parent; parent validates + calls API + posts back the result.
3. Constrained runtime API handed to the generated module.
4. Backend: `render_code` column + storage + size limit; write-path unchanged.
5. AI tool spec + approval/preview card rendering inside the sandbox.
6. **Security tests**: assert the frame cannot read `document.cookie`, cannot
   call `/api/**` (CORS/credential-less), cannot reach `window.parent`.

---

## 6. Sequencing

Ship the declarative **"expand the builder"** options first (column colors,
alignment, formatting, default sort, alternate views) — they cover most
"make it look different" needs with zero new risk. Reach for this sandbox only
for truly custom widgets the declarative layer can't express.
