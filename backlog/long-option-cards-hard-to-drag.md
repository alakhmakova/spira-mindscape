# Long option cards are hard/impossible to drag-reorder

**Status:** ✅ Fixed

## Summary

On the Options tab, an option with a lot of text (e.g. 200–500 characters) renders a very tall
card. On mobile the card can be taller than the viewport, and reordering by drag becomes very hard
or impossible: the card only shuffles while the drag handle stays on screen, and to move one slot
the finger must travel a full card-height — which doesn't fit on the screen.

## Steps to reproduce

1. Open a goal → Options tab (mobile / narrow viewport).
2. Add a strategy with a very long text so its card is taller than the screen.
3. Long-press the drag handle and try to drag it to another position.
4. The card barely moves and can't be dragged to the target — the handle scrolls out of view.

## Root cause

`OptionsList.tsx` `startDrag` computes the reorder step as the dragged card's full height
(`li.getBoundingClientRect().height + LIST_GAP_PX`). For a card taller than the viewport, one slot
of travel exceeds the screen, and there is no auto-scroll, so the pointer can't move far enough.

## Fix approach

- **Applied (via the Options redesign):** an option longer than 3 lines is **collapsed to 3 lines**
  with a "Show more"/"Show less" toggle, and while dragging the card is **forced to the collapsed
  view** (`InlineText` `clampLines` + `forceCollapsed`, wired from `OptionRow`). A collapsed card is
  always short, so it stays on-screen and draggable; the reorder step is also capped
  (`DRAG_STEP_MAX_PX`) so small finger moves reorder one slot. Short cards are unaffected.
- **Auto-scroll (done):** during a reorder drag, `OptionsList.tsx` `startDrag` runs a
  `requestAnimationFrame` loop that scrolls the window toward the top/bottom edge when the pointer is
  near it, and folds the accumulated scroll into the drag math (`scrolledBy`) so reordering continues
  as the page scrolls — so a **long list / many cards** can be traversed to an off-screen position.
- Reordering now happens only in **reorder mode** (whole card is the drag target, all card actions
  disabled), which also removed the tiny handle that used to scroll out of view.

## How to verify fixed

- A very long option collapses to 3 lines during drag; dragging reorders with small finger movements.
- With a long list, dragging a card and holding near the bottom/top edge **auto-scrolls** the page and
  drops the card at an originally off-screen position. Verified via Playwright at a 390px viewport
  (auto-scroll increases `window.scrollY`; whole-card drag reorders and persists).

## Resolution

Fixed in `src/components/spira/OptionsList.tsx` (reorder mode + whole-card drag + auto-scroll) and
`src/components/spira/Inline.tsx` (`readOnly`, float-compatible clamp). Per-drop persistence reuses
the existing `reorderOptions(goalId, optionIds)` full-list mutation.
