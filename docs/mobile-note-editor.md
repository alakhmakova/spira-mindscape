# Mobile note editor (WebView + TipTap)

## Why

Resource **notes** are authored on the web with a TipTap rich-text editor and stored as **HTML**.
An earlier attempt used a native Compose rich-text library, but it crashed on paste-over-selection
(data loss), lacked reliable text selection and active toolbar states, and mishandled colour. To get
**true parity** with the web and a robust editor, the mobile app hosts the **same TipTap editor**
inside a `WebView`.

## How it fits together

1. **The editor bundle** — `embeds/note-editor/` (`index.html` + `main.ts`) is a vanilla
   `@tiptap/core` editor configured with the **same extensions as the web**
   (`src/components/spira/RichTextEditor.tsx`): StarterKit (headings, bold/italic/strike/code,
   blockquote, code block, horizontal rule, undo/redo history, bullet/ordered lists, link,
   underline), Highlight, TaskList/TaskItem, TextStyle + Color. It renders its own toolbar with
   active states (`editor.isActive(...)`, refreshed on `selectionUpdate`/`transaction`).

2. **Build** — `npm run build:note-editor` (`vite.note-editor.config.ts` + `vite-plugin-singlefile`)
   bundles everything into **one self-contained file** with no external requests (CSP/offline safe),
   written to `android/app/src/main/assets/note-editor/index.html`. **Commit that asset.** Re-run the
   build whenever `embeds/note-editor/*` changes.

3. **The bridge** — the page exposes:
   - `window.spiraSetContent(html)` — seed content (`emitUpdate:false`).
   - `window.SpiraNote.onChange(html)` — called by the page (debounced ~400ms, and immediately on
     blur) whenever the note changes.
   - `window.spiraGetText()` / `window.spiraGetHtml()` / `window.spiraFlush()`.

4. **The Android host** — `ui/goals/NoteWebEditor.kt` loads the asset in a `WebView`, registers the
   `SpiraNote` JS interface (marshalling to the main thread), seeds content **once** on
   `onPageFinished` (so typing never resets the caret), and forwards `onChange` to `onHtmlChange`,
   which the caller persists **optimistically** via `updateResource`. Used inline in the note card,
   in the create sheet, and full-screen (`ResourceFullScreen`).

## Keyboard

`MainActivity` uses `android:windowSoftInputMode="adjustResize"` and the full-screen editor applies
`Modifier.imePadding()`, so the WebView shrinks above the keyboard and the end of a long note stays
reachable.

## Testing

Robolectric can't meaningfully render a WebView, so the note editor is verified on the emulator/
device (and the bundle can be sanity-checked in a browser: `npm run build:note-editor`, then serve
`android/app/src/main/assets/note-editor/index.html` and exercise the toolbar + `spiraSetContent` /
`SpiraNote.onChange`). Visual-check tests avoid expanding a note card for this reason.
