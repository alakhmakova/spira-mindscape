# Android note editor: selected text can't be copied — no selection menu appears

- **ID:** BUG-032
- **Status:** ✅ Fixed — **third time, and this time with the cause measured** (2026-08-18). It was
  closed on 2026-08-08 with a workaround, reopened and "fixed at the source" on 2026-08-17 on a
  diagnosis that does not reproduce, and reopened again by the owner (GRO-137) as: one word gets
  selected, there are no handles to widen it, and the platform's "Select all" bubble fights the
  app's own Copy card. See **Resolution — 2026-08-18** at the foot; the two sections above it are
  kept because they record what was tried.
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

## Root cause — the real one (2026-08-17)

The 2026-08-08 analysis stopped one step short. It concluded that "Chromium … does not regard the
range as copyable editable text", and worked around it. Re-diagnosed on an emulator by serving the
**same** `note-editor/index.html` over HTTP and opening it in **stock Chrome** — no Spira WebView
involved at all:

| Gesture | What Chromium did |
|---|---|
| Long-press a word | **one caret teardrop**, menu offering only **Select all** |
| "Select all" | **two drag handles**, menu offering **Cut · Copy · Select all · Read aloud** |

That pair settles it. Chromium is perfectly willing to draw handles and a full menu on this page —
it does so for "Select all". The long press was **not producing a range at all**: it produced a
*collapsed caret*, and for a caret Chromium correctly shows one handle and no Copy.

Two things follow:

1. It is not the WebView host. It reproduces in plain Chrome, so `setOnLongClickListener`
   consuming the event was never the cause (it only hid what little Chromium would have shown).
2. **`sel.addRange(...)` from outside the editor cannot work.** ProseMirror owns the selection and
   re-asserts its own on the next tick, collapsing ours. The old code even documents the symptom —
   "ProseMirror reasserts its own selection in the same tick, so `getSelection().toString()` comes
   back empty" — and worked around it by reading the range object instead of asking why.

## Resolution

**2026-08-17 — fixed at the source: select THROUGH ProseMirror, not around it.**

`window.spiraSelectWordAt(x, y)` in `embeds/note-editor/main.ts` now maps the point with
`view.posAtCoords`, expands to the word in the text block, and calls
`editor.chain().focus().setTextSelection({ from, to })`. ProseMirror then holds a real range,
renders it, and Chromium decorates it. `window.spiraSelectedText()` reads
`state.selection` rather than the DOM, so the text is never empty.

The long press is **no longer consumed** (`setOnLongClickListener` returns `false`), which is what
lets Chromium attach its **native drag handles** and its full Cut / Copy / Paste menu to the
selection — the thing the owner asked for.

Verified over the Chrome DevTools Protocol against the real page on an emulator:

```
before:  domSelCollapsed = true                    ← the bug
after:   afterDomCollapsed = false
         afterDomText     = "brown"
         afterPmText      = "brown"
```

and the word is visibly highlighted as a range rather than merely underlined by a caret.

### What the earlier fix left behind (kept)

The app's own bubble stays, now carrying **Copy · Cut · Paste** — it works even where a platform
menu is suppressed, and it is the app's own shape. What follows is that earlier work, unchanged:

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


---

## Root cause — the measured one (2026-08-18)

The 2026-08-17 section claims that "serving this exact page in stock Chrome, a long press produced
one caret handle and a menu offering only Select all". **That does not reproduce.** Re-run on the
`spira_pixel` emulator, serving the very same `android/app/src/main/assets/note-editor/index.html`
over HTTP:

| Where | Long press on a word |
|---|---|
| **Stock Chrome**, this page | selects the word, **two drag handles**, **Cut · Copy · Select all · Read aloud** |
| Stock Chrome, a bare `contenteditable` | the same |
| **The app's WebView**, this page, with every line of the app's own selection code removed | **an insertion caret** and a one-item **Select all** |

The third row is the finding. With `setOnLongClickListener`, `setOnTouchListener` and the app's
bubble all deleted, and with the editor focused first so that cannot be the difference, the WebView
still drops a caret where Chrome takes the word.

So it was never the page, never ProseMirror, and never the app consuming the event: **Android's
WebView and Chrome do not treat a long press in editable content the same way**, and the difference
is in the host, which a page cannot reach. Chromium then behaves correctly for what it has — one
handle and no Copy is right *for a caret*.

Two consequences:

1. **The platform's selection cannot be used here.** Waiting for it, or nudging it, is what the
   previous two attempts did.
2. **A programmatic range gets no platform handles either.** `setTextSelection` gives ProseMirror a
   real range and Chromium renders the highlight, but it raises none of its own handles for it —
   which is exactly what the owner saw on 2026-08-17: a highlighted word and nothing to drag.

The confusion the owner reported on top of that was self-inflicted: the 2026-08-17 change stopped
consuming the long press, so Chromium's caret bubble opened **beside** the app's Copy card. Two
menus, one gesture.

## Resolution — 2026-08-18

**One selection system, and it is the app's — complete this time.**

- The long press is **consumed again** (`setOnLongClickListener` returns `true`), so Chromium's
  one-item bubble never opens. Nothing else about touch is intercepted: `setOnTouchListener` only
  records where the finger went down and clears any open selection, and returns `false`.
- `window.spiraSelectWordAt(x, y)` selects the word **through ProseMirror**, as before.
- **The app draws the two drag handles** (`SelectionHandles` in `NoteEditorActivity.kt`) — teal
  teardrops under each end of the highlight, in the platform's own shape. Dragging one calls
  `window.spiraMoveSelectionEnd("start" | "end", x, y)`, which maps the point with `posAtCoords`
  and re-selects through ProseMirror; pulling one end past the other swaps them rather than
  emptying the range. **This is what makes a range, rather than one word, selectable.**
- **One menu** (`SelectionMenu`): **Copy · Cut · Paste · Select all**, on the app's usual floating
  white card, above the selection where there is room and below it where there isn't. "Select all"
  is on it because it was the one thing the platform's bubble did offer.
- The JS half is a documented section of `embeds/note-editor/main.ts`; every call answers with the
  same JSON — the selected text and where each end sits in CSS pixels — so the overlay redraws from
  any of them.

**Verified on the emulator** (`NoteEditorActivity` launched directly with seeded HTML, so no Google
sign-in is needed — the cheap reproduction from the 2026-08-08 section still works):

1. Long press on "brown" → the word is highlighted, both teal handles appear, one menu, and **no**
   platform bubble.
2. Dragging the right-hand handle across the line grew the selection to
   "brown fox jumps over the la", with the handles and the menu following.
3. **Copy** put exactly that string on the clipboard — Android's own clipboard preview showed it.

Not covered by an automated test: the editor is a WebView driving real JS, which Robolectric cannot
exercise. The emulator run above is the regression check.
