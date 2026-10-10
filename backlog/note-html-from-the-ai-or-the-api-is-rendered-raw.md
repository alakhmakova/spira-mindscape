# Note HTML from the AI or the API is put on the page as it is, handlers included

- **ID:** BUG-099
- **Status:** ✅ Fixed
- **Reported by:** Found in a security review the owner asked for (2026-10-10: "что если
  пользователь будет делать SQL injections или другие типы атак через поля для ввода
  информации?") — not by a user, and not by any test
- **Area:** Web / security (`components/ai/AiPanel.tsx`, `components/spira/Resources.tsx`,
  `components/spira/note-export.ts`)
- **Severity:** **Medium** — stored and prompt-injected XSS. In production the CSP blocks inline
  handlers, so the realistic impact there is limited; on the dev server and in any browser that
  ignores the header there was no second layer

## Summary

A note's body is **HTML** (TipTap's), and the server stores whatever HTML it is sent — there is no
sanitiser on the backend. Three places on the web then put that HTML into a **live** DOM:

| Where | How | What it meant |
|---|---|---|
| `AiPanel` content preview | `dangerouslySetInnerHTML={{ __html: body }}` | The body of a note, **or of an AI proposal**, rendered as markup |
| `Resources.tsx` `stripHtml` (Copy) | `document.createElement("div").innerHTML = html` | Copying a note as text **loaded its images and fired their `onerror`** |
| `note-export.ts` (TXT / DOC / PDF) | the same detached-`div` pattern, then `win.document.write(...)` into a print window | An export ran whatever the note carried, in a same-origin window |

A detached `div` looks harmless and is not: an element of the live document **fetches `<img>`
sources and fires their handlers** even when it is never attached to the page.

**Why it is not only self-XSS.** A user writing `<img onerror>` into their own note attacks only
themselves. But the **AI coach writes note bodies too** (`note` / `edit_note` / `new_goal`
proposals), and the coach reads untrusted content — attached documents and fetched URLs. A prompt
injected through one of those can put markup into a proposal, and the preview rendered it. That is
the path from someone else's text to script on the user's page.

**What was already safe**, and why this is Medium rather than High:

- **SQL injection** — not possible: every query is parameterised (JPA, and `?` placeholders in the
  two hand-written SQL statements). Input text never becomes SQL.
- **The CSP** — `script-src 'self'` without `'unsafe-inline'` blocks inline event handlers in
  production, and a new `about:blank` print window inherits it. So the header was quietly the only
  defence, and the dev server sends no header at all.
- **The note editor itself** — TipTap parses content through its schema, which drops what it does
  not know. Links are filtered by `isSafeHttpUrl`.

## Steps to reproduce

Locally (dev server, no CSP):

1. Store a note whose body is markup, through the API rather than the editor:
   ```
   mutation { createResource(goalId: "<id>", input: { type: "note", title: "x",
     body: "<p>hi</p><img src=x onerror=\"alert(document.cookie)\">" }) { id } }
   ```
2. On the goal page, open the note's menu and **Copy** it, or **Export → PDF**.
3. **Before the fix:** the alert fires — from a copy-as-text and from an export, neither of which
   ever shows the image. Opening the same note as an attachment in the AI panel's preview fires it
   too.

The prompt-injection path is the same sink with a proposal body in place of a stored note.

## Root cause

Two habits, both common:

1. **Trusting HTML because our editor wrote it.** The editor is not the only writer: the API
   accepts any string, and the model's output is untrusted input by definition (§5b of
   `docs/security-model.md` says so for the model's *instructions*; nothing said it for its
   *markup*).
2. **`createElement("div").innerHTML = html` as a "parse only" step.** It is not parse-only in the
   live document. `document.implementation.createHTMLDocument()` is.

## Fix approach

One module, `src/lib/spira/safe-html.ts`, used by every place that renders or reads stored note
HTML:

- **`inertContainer(html)`** — parses in a document created by `createHTMLDocument`, where nothing
  loads and nothing runs. `htmlText(html)` reads text through it (Copy, the TXT export).
- **`sanitizeNoteHtml(html)`** — an **allowlist** of what TipTap produces (paragraphs, headings,
  marks, lists, task items, links, code, quotes, tables), never a blocklist:
  - every attribute not listed is removed — every `on*` handler with it;
  - `href` keeps only `http:`, `https:` and `mailto:`, judged by the URL parser, so
    `jav&#x09;ascript:` is caught; links get `rel="noopener noreferrer nofollow"`;
  - `style` keeps only `color` / `background-color` with a plain colour value — the editor's text
    colour and highlight — and nothing that can move or cover the page;
  - `<input>` can only ever be a disabled checkbox (a task item's box);
  - `script`, `style`, `iframe`, `object`, `embed`, `svg`, `math`, `template`, `form` and the like
    go **with their contents**; any other unknown tag is unwrapped and its text kept.
- Wired into the AI preview, `stripHtml`, and `prepareExportHtml` / `htmlToPlainText`, so the
  print window only ever receives sanitised markup.

The CSP stays as the second layer, and is no longer the only one.

## How to verify fixed

- `src/lib/spira/safe-html.test.ts` (15 tests): inline handlers, `<script>`, `javascript:` /
  `data:` / obfuscated links, `<iframe>`, `<svg>`, `<style>`, `<form>`, comments and
  page-covering `style` are all removed, while a real note — headings, marks, task list, ordered
  list with `start`, highlight colour, safe links, code — survives intact.
- `e2e/note-html-is-inert.spec.ts` — in real Chromium, where images load and handlers fire (jsdom
  does neither): reading `<img src=… onerror=…>` through `htmlText` / `sanitizeNoteHtml` makes **no
  request for the image and runs no handler**. A **control** in the same test does it the old way,
  in a live detached `div`, and asserts the handler **does** fire — so the spec cannot pass against
  nothing.
- By hand: the reproduction above, after the fix — Copy and Export produce "hi" and nothing fires.

## Resolution

Fixed 2026-10-10. Files: `src/lib/spira/safe-html.ts` and `safe-html.test.ts` (new),
`e2e/note-html-is-inert.spec.ts` (new), `src/components/ai/AiPanel.tsx`,
`src/components/spira/Resources.tsx`, `src/components/spira/note-export.ts`,
`docs/security-model.md` (new §5d and the quick map).

Three things worth keeping:

- **The model's words are untrusted input — its markup as much as its instructions.** Anything it
  writes that reaches a sink is treated like text from a stranger.
- **"Detached" is not "inert".** If HTML has to be parsed to read it, parse it in a document of its
  own.
- **Still open, deliberately:** the backend stores note HTML unsanitised. Every web sink now
  sanitises on the way out. Android's note editor is a WebView running the same TipTap bundle,
  which parses through its schema. Sanitising on the way in as well (e.g. jsoup's `Safelist`) is
  worth doing if any new surface — an email, a share link — ever renders a note outside these
  paths.
