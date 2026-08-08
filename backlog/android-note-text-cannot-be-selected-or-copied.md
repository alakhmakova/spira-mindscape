# Android note editor: selected text can't be copied — no selection menu appears

- **ID:** BUG-032
- **Status:** ✅ Fixed (2026-08-08) — verified on an emulator; pending manual commit by the user
- **Reported by:** User (2026-08-07)
- **Area:** Android — note editor (`ui/goals/NoteEditorActivity.kt`, the TipTap WebView)
- **Type:** Defect

## Summary

Selecting a passage inside a note on Android offers **no way to copy it**. Either the system's
text-selection menu (Copy / Select all / Share) never appears at all, or it appears without a
Copy entry. Getting a quotation out of a note is therefore impossible on the phone — the user has
to retype it.

## Steps to reproduce

1. Open a goal → Resources → open a note.
2. Long-press a word in the note body to start a selection, and drag the handles.
3. Expected: the usual floating Copy / Select all menu. Actual: no menu, or one without Copy.

## Root cause — reproduced on an emulator (2026-08-08)

Reproduced by launching `NoteEditorActivity` directly with seeded HTML (`adb root` +
`am start … -e initialHtml '<p>Select me and copy this sentence.</p>'`), which reaches the editor
without a Google sign-in. Findings, in order:

1. **Long-press in the note body never selects the word.** It only drops a caret, and the floating
   menu that appears offers a single item: **Select all**.
2. **Selection itself works.** Tapping "Select all" highlights the text and shows both drag
   handles — so nothing is suppressing selection.
3. **Even with that live selection, the menu still offers only "Select all"** — no Copy, no Cut.
   That is the actual defect: the menu is missing its items, not the selection.
4. **The same Activity's native field is fine.** Long-pressing the Compose note *title* selects a
   word and shows the full **Copy · Cut · Select all**. So the window, the theme
   (`android:Theme.Material.Light.NoActionBar`) and the floating-toolbar mechanism all work.
5. **Nothing in the page blocks it.** The bundled editor's stylesheet is 2 263 characters and
   contains zero occurrences of `user-select`, `-webkit-user-select`, `touch-callout` or
   `pointer-events`. The Kotlin touch listener returns `false`, so it consumes nothing.
6. Focusing the editor first (tap into the body → caret + IME appear) changes nothing: long-press
   still only moves the caret.

So the CSS and the ActionMode-hosting theories are both **ruled out**. What is left is the
WebView's own selection path: Chromium builds Cut/Copy from the *renderer's* view of the
selection, and with ProseMirror managing selection it does not regard the range as copyable
editable text — so it contributes only the "Select all" item.

## Fix approach

Chromium's menu is not ours to populate, and fighting it is what makes this fragile. The app
already owns a JS bridge to this page (`window.spiraCmd` / the `SpiraNote` interface), so the
reliable route is to **carry the copy ourselves**:

- Expose a bridge call that returns `window.getSelection().toString()` (and a `selectWordAt` for
  long-press, so a press selects the word under the finger the way every other Android field
  does).
- Put the copied text on the clipboard from Kotlin — `ui/goals/ResourceActions.kt` already has a
  clipboard path worth reusing — and confirm with the app's own toast/banner.
- Offer it through the app's own menu (`SpiraDropdownMenu`), anchored at the selection, rather
  than hoping Chromium adds an item.

Whatever is built must keep **editing** working: the WebView exists precisely because the embedded
editor never got IME focus otherwise.

## How to verify fixed

- Long-press in a note body selects the word under the finger.
- A selection offers **Copy**; the copied text pastes elsewhere intact, including formatting-free
  plain text.
- Typing, formatting and autosave still work.
- Re-run the emulator reproduction above — it needs no account, so it can be repeated cheaply.

## Resolution

The app now carries the copy itself, because Chromium's menu is not ours to populate.

- `NoteEditorController.selectWordAt(x, y)` runs JS in the page: it finds the caret position under
  the press (`caretRangeFromPoint`, with `caretPositionFromPoint` as the standard fallback),
  expands it to the surrounding word, selects it, and returns the word plus its rectangle.
  It reads the text from the **range**, not from `getSelection()` — ProseMirror reasserts its own
  selection in the same tick, so the selection object comes back empty. That was the one real trap
  in this fix and cost a build cycle to see.
- The WebView's `setOnLongClickListener` fires that and **consumes** the event, so Chromium's
  one-item menu no longer opens on top of ours. Touch coordinates are converted from view pixels
  to CSS pixels on the way in.
- `CopySelectionBubble` is the app's own floating white card (hairline + shadow, like every Spira
  menu), placed above the word, flipping below it when the word sits too near the top to leave
  room. Tapping it reads the live selection — so widening the word with the handles still works —
  and falls back to the captured word, then writes to the clipboard via the existing
  `copyPlainText`, confirming with a toast.

**Verified end to end on the emulator**: long-press selected `sentence.`, the bubble appeared
under the line, Copy was tapped, and pasting into the note title turned `T` into `Tsentence.`

Not covered by an automated test: the editor is a WebView driving real JS, which Robolectric
cannot exercise. The emulator reproduction in this file is the regression check.
