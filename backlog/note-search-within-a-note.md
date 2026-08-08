# Search within a note (both surfaces)

- **ID:** BUG-033
- **Status:** 🐞 Open
- **Reported by:** User (2026-08-07)
- **Area:** Note editor — Android (`ui/goals/NoteEditorActivity.kt`, `NoteToolbar.kt`) and web
  (the note editor in `src/components/spira/`)
- **Type:** Enhancement

## Summary

A note can grow long, and there is no way to **find a word inside it**. The only option is to
scroll and read. Both surfaces need a find-in-note control.

## Desired behaviour

- A **search field** in the note's own chrome (not the goal search — that switches goals, and the
  two must never share state, per the screen-local search rule in CLAUDE.md).
- Typing highlights every match in the body and shows a **count** ("3 of 12").
- **Next / previous** step through matches and scroll each into view.
- **Escape / close** clears the highlighting and leaves the text exactly as it was — search must
  never modify the note.
- Matching is case-insensitive by default.
- Same behaviour and same wording on both surfaces.

## Notes

- Both editors are **TipTap**, so the same approach fits: a search-and-highlight decoration over
  the document rather than editing content. Highlights are **decorations**, never marks — a mark
  would be written into the note and autosaved, permanently altering it.
- Android's note body is TipTap in a `WebView`, so the field is Compose chrome driving the page
  through the existing `window.spiraCmd` bridge — the same route `NoteToolbar` already uses.
- Worth doing after `android-note-text-cannot-be-selected-or-copied.md` (BUG-032): both touch
  selection inside the same WebView, and that one is a defect while this is an addition.

## How to verify

- Typing a word highlights every occurrence and reports the count; next/prev walk them.
- Closing the search leaves the note's HTML byte-identical to before it opened.
- Works on both surfaces with the same words and the same shortcuts.
