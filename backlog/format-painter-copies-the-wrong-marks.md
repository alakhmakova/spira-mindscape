# Format painter copies the formatting of the text *before* the selection

- **ID:** BUG-039
- **Status:** ✅ Fixed (2026-08-18) — see Resolution.
- **Reported by:** User (2026-08-18, `GRO-137`)
- **Area:** Both surfaces — `embeds/note-editor/main.ts` (Android note editor) and
  `src/components/spira/RichTextEditor.tsx` (web)
- **Type:** Defect

## Summary

The format painter does nothing. Press the brush with a formatted word selected, select another
word, press the brush again — the formatting is not applied.

The owner's words: "при нажатии на кисточку для копирования форматирования и потом повторении
нажатии для применения форматирования формат не применяется".

## Steps to reproduce

1. Open a note whose body is `Normal text **bold word** then plain again here.`
2. Long-press "bold" to select it (it is visibly bold).
3. Tap the **format painter** (the brush). The toast says
   **"Plain formatting copied — select text to apply it"** — the first sign of the defect.
4. Select "plain" and tap the brush again. Nothing changes.

## Root cause — measured on an emulator (2026-08-18)

The bridge was logged, and for a selection that was visibly bold it sent:

```
SpiraPainterDbg: bridge json={"picked":[]}
```

So **the copy is what is broken**; the apply step is correct — it faithfully applies the empty set
of marks it was given, which is why nothing happens and why nothing said so.

The copy read `editor.state.selection.$from.marks()`. ProseMirror resolves the marks *at* a
position, and at a position sitting **on a boundary** it answers with the marks of the node
**before** it (`ResolvedPos.marks()`: with no text offset it takes `parent.maybeChild(index - 1)`).
A word selection starts exactly on such a boundary, so selecting a bold word in a plain sentence
asks for the marks of the plain run in front of it and gets none.

It only looks right in the one case where there is nothing before the selection — a whole paragraph
in one style — which is presumably how it passed review.

**The web had the identical line**, so the same bug: `RichTextEditor.tsx` `formatPainter`.

## Fix approach

Ask the other question. `ResolvedPos.marksAcross($to)` returns the marks of the node **after**
`$from` — i.e. of the text actually selected — which is what "copy this text's formatting" means.
For a bare caret there is no "after", so the stored marks (what the next keystroke would carry) are
the honest answer.

## How to verify fixed

- Select a bold word → brush → the toast says **"Bold copied — select text to apply it"**, not
  "Plain formatting copied".
- Select a plain word → brush → **"Bold applied"**, and the word is bold.
- Copying from plain text still copies nothing, and applying it clears the target's formatting —
  the toast for that says "Formatting cleared from the selection" on both surfaces now.

## Resolution

**2026-08-18 — fixed on both surfaces.** `marksToCopy()` in `embeds/note-editor/main.ts` and the
same three lines in `RichTextEditor.tsx` `formatPainter` now use
`sel.$from.marksAcross(sel.$to) ?? sel.$from.marks()` for a range, and
`storedMarks ?? $from.marks()` for a caret.

Android's `describePainter` also picked up the web's wording for the empty case
("Formatting cleared from the selection"), which it did not have.

Verified on the emulator with `Normal text **bold word** then plain again here.`: brush on "bold"
→ "Bold copied — select text to apply it"; brush on "plain" → "Bold applied", and "plain" rendered
bold.

> A note on how long this took to find: the first three attempts tapped the **highlighter**, whose
> glyph sits where the brush appears to be and which produces no toast at all. The format painter
> is further along the scrolling toolbar. Log the bridge before trusting a button.
