# Android: Options cards should support drag-and-drop reordering

- **ID:** BUG-010 (enhancement — tracked here at the user's request)
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** User
- **Area:** Android — `ui/goals/GoalWorkspaceScreen.kt` (`OptionsTabContent`, `OptionCard`)
- **Severity:** Low (nice-to-have UX; a working alternative already ships)

## Summary

The Options tab (2026-07-18) lets a user reorder option cards by editing the "Option N" number
inline (tap the number, type a new position, commit) — `GoalWorkspaceViewModel.reorderOptions`
and the `reorderOptions` GraphQL mutation already support moving a card to any position. The user
also asked for **visual drag-and-drop** reordering (long-press a card and drag it into place) as
an additional, more natural way to reorder on mobile. This was explicitly deferred: "не делай,
запиши в backlog" (don't do it now, log it).

## Steps to reproduce / current behavior

Not a bug — this is a missing feature. Today: open a goal → Options tab → tap the number on any
option card ("Option 2" etc.) → type a different number → commit (blur or Done) → the card moves
to that position and everything shifts around it. Works, but there's no drag gesture.

## Root cause

N/A — not implemented yet. Compose has no built-in drag-to-reorder for a plain `Column`/list of
cards; it needs either:

- A hand-rolled `pointerInput` drag implementation (track a dragged item's offset, compute
  hover-target index from finger position, animate other items sliding to make room, commit the
  new order on release) — the standard approach before Compose's official reorderable APIs
  matured.
- A small third-party library (e.g. `sh.calvin.reorderable` or similar) — evaluate license/size
  before pulling one in, per this repo's "reuse the design system, don't add dependencies
  lightly" convention.

Either approach needs to coexist with the existing long-press-for-menu gesture on the same card
(`OptionCard`'s `combinedClickable(onLongClick = { menuMode = true })`) — a long-press currently
opens the Delete/Make active/Exit menu, so drag would need a different trigger (e.g. a dedicated
drag handle icon, or distinguishing "long-press-then-hold-still" from "long-press-then-move").

## Fix approach (not yet implemented)

1. Add a small drag-handle affordance to each `OptionCard` (e.g. a grip icon), so drag doesn't
   collide with the existing long-press-opens-menu gesture.
2. Implement drag via `pointerInput` + `Modifier.offset` on the dragged card, reordering the
   in-memory list live as the drag crosses neighboring cards' midpoints (standard reorderable-list
   pattern), and call `actions.onReorderOption(draggedId, finalPosition)` on release — reusing the
   `reorderOptions` action that already exists (no new backend/ViewModel work needed).
3. Keep the manual number-entry reordering working as a fallback/precision option — don't remove
   it.

## How to verify fixed

1. Long-press-and-drag an Option card by its handle; it visually follows the finger, other cards
   slide to make room, and releasing commits the new order (persists after a refetch).
2. The in-card Delete/Make active/Exit long-press menu still works (no gesture conflict).
3. Manual number-entry reordering still works.
4. Visual check per `VisualCheckTest` conventions (drag mid-state doesn't need a screenshot test,
   but the before/after settled states should look correct).

## Resolution

✅ **Implemented on Android** (and subsequently on web too).

- **Android** (`ui/goals/GoalWorkspaceScreen.kt`): `OptionCard` drags via
  `detectDragGesturesAfterLongPress` (long-press to pick up, then drag), with the card lifted
  using `zIndex` + `Modifier.offset`. `OptionsTabContent` holds the live `order` / `draggingId`
  state, shuffles neighbours during the drag, and commits once on release through
  `GoalWorkspaceViewModel.reorderOptions(id, index)` → `ReorderOptionMutation`. A short tap still
  focuses the card text for inline editing, so the two gestures coexist. The number-entry
  reordering it replaced is gone (the "Option N" number is now display-only).
- **Web** (`src/components/spira/OptionsList.tsx`): the same interaction model, but grabbed via a
  dedicated grip handle (the card body hosts an inline text field, so press-and-drag there would
  fight text selection), with ↑/↓ on the focused handle as a keyboard fallback. This replaced the
  old ↑/↓ arrow buttons.

Both surfaces are documented in **`docs/drag-and-drop-options.md`** — including what each replaced
and the `pointer-capture` bug that made web down-drags freeze (fixed by attaching
`pointermove`/`pointerup` to `window` instead of the handle).

Verification: web — `e2e/options.spec.ts` "dragging a card downward reorders it" (Playwright mouse
drag) passes; Android — built and exercised manually on the emulator, `VisualCheckOptionsTabTest`
renders the tab.
