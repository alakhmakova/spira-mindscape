# Drag-and-drop reordering for Options cards (web + Android)

Options are the GROW "O" step: a list of candidate strategies whose **order carries meaning**
(the user ranks them). Both surfaces let the user drag a card into a new position; this document
records **what shipped, how it works on each surface, and what it replaced**.

---

## What was there before (and why it was replaced)

Neither surface started with drag-and-drop. Both used a "type/tap the position" substitute,
which worked but felt clumsy — reordering is a spatial task and both replacements were verbal.

| Surface | Before | Problem |
|---|---|---|
| **Web** | Two small **↑ / ↓ arrow buttons** on each card (`ArrowUp` / `ArrowDown`, disabled at the ends). Each click moved the card exactly one slot. | Moving a card from position 5 to 1 meant **four separate clicks**, each triggering its own optimistic update + server write. No sense of grabbing the item; on touch the 20×28px targets were fiddly. |
| **Android** | The **"Option N" number was inline-editable** — tap it, type a new number, commit (blur/Done) and the card jumped to that position. | Reordering by *typing a number* is indirect: the user must translate "put this second" into an edit action, and it needs a keyboard on a touch device. Logged as **BUG-010** ("Options cards should support drag-and-drop reordering") for exactly this reason. |

Android then briefly used **long-press-anywhere** to pick a card up. That went away when the
Options screen was rebuilt to match the web (2026-08-10): both surfaces now use the same
**reorder mode**, described below.

Both are now gone, replaced by a real drag gesture. The shared backend contract
(`reorderOptions(goalId, optionIds)`) did not change — only the way the user expresses the move.

---

## Shared model (both surfaces)

The interaction is the same on both platforms, which is what keeps them in parity:

1. **Local order state.** The list keeps its own array of option ids (`order`), seeded from the
   server-sorted options.
2. **Live shuffling.** While the finger/pointer moves, the *local* array is reordered as the
   card crosses each slot, so neighbours visibly make room **during** the drag — not on release.
3. **Commit on release only.** One `reorderOptions` mutation fires when the drag ends, and only
   if the index actually changed. Dragging and dropping in place writes nothing.
4. **Re-seed from source, never mid-drag.** When the source list changes (add / remove / refetch),
   the local order re-syncs — but the sync is skipped while a drag is in progress so a background
   refresh can't yank the card out from under the finger.
5. **The dragged card is lifted** (raised above its neighbours and offset to follow the pointer)
   so it reads as "picked up".

---

## Web

**File:** `src/components/spira/OptionsList.tsx`

### Gesture: a reorder MODE, not a handle

There is no drag handle and no long press. A **Reorder** button in the Options section header
(shown only from two options up) puts the list into reorder mode; while it is on, the **whole
card** is the drag target and every per-card control — the active radio, the rating badge, the ⋯
menu, the inline editor, the Show more toggle — goes inert, so a touch anywhere moves the card
instead of editing it. Pressing **Save** leaves the mode.

The mode exists because the card's text is an inline-editable field: without it, a press-and-drag
starting on the text would fight with placing the caret and selecting words. A handle was the
earlier answer, but it gave a 20×28px touch target; making the whole card grabbable for the
duration of an explicit mode is both bigger and unambiguous. `touch-action: none` on the card
stops the browser scrolling the page instead of dragging.

While dragging, the card is forced to its collapsed (≤3-line) view, and the reorder step is capped
at `DRAG_STEP_MAX_PX` — otherwise a strategy taller than the screen would need a full card-height
of finger travel to move one slot, which is impossible on a phone.

### Why the listeners live on `window`

This is the subtle part, and it was a **real bug** (dragging *downwards* froze mid-drag):

```tsx
// on the handle: start only
onPointerDown={(e) => startDrag(e, opt.id)}

// inside startDrag: move/up are attached to WINDOW, not the handle
window.addEventListener("pointermove", onMove);
window.addEventListener("pointerup", onUp);
window.addEventListener("pointercancel", onUp);
```

The first implementation attached `pointermove`/`pointerup` to the handle itself and used
`setPointerCapture`. But the drag **reorders the DOM**, so React moves the `<li>` (and the handle
inside it) to a new position in the list. When that node is detached/re-inserted, Chrome
**drops the pointer capture**, the element stops receiving `pointermove`, and the drag silently
dies. It bit downward drags first because dragging down moves the dragged node past more
siblings. Listening on `window` makes the drag independent of the element's identity, so DOM
reordering can't interrupt it. (Same pattern as the AI panel's resize handle.)

### Computing the target slot

```tsx
const step = li.getBoundingClientRect().height + LIST_GAP_PX; // one card + the 12px gap
const slots = Math.round(total / step);                       // whole slots travelled
const toIndex = clamp(fromIndex + slots, 0, order.length - 1);
setDragOffset(total - (toIndex - fromIndex) * step);           // leftover → card tracks the finger
```

The card is translated by the *leftover* distance after whole-slot swaps, so it follows the
pointer smoothly instead of snapping between slots.

### Auto-scroll

While the pointer sits within 64px of the top or bottom of the viewport the page scrolls toward
it (speed ramps with proximity), and the accumulated scroll is folded into the drag math — so a
list longer than one screen can be traversed in a single drag.

### Store / server

`reorderOptions(goalId, from, to)` in `src/lib/spira/store.ts` splices the array optimistically,
then (debounced) sends the full id list to `spiraApi.reorderOptions`, replacing the local list
with the server's response.

---

## Android

**File:** `android/app/src/main/java/com/spiramindscape/android/ui/goals/GoalWorkspaceScreen.kt`
(`OptionsTabContent` + `OptionCard`)

### Gesture: the same reorder mode as the web

Since the 2026-08-10 parity pass Android has no long press either. The `Reorder`/`Save` button in
the tab header flips `reordering`, and only in that state does the card carry a drag detector:

```kotlin
.then(
    if (reordering) {
        Modifier.pointerInput(option.id) {
            detectDragGestures(               // plain drag — NOT ...AfterLongPress
                onDragStart = { onDragStart() },
                onDragEnd = onDragEnd,        // commits via the ViewModel
                onDragCancel = onDragEnd,
                onDrag = { change, amount -> change.consume(); onDragBy(amount.y) },
            )
        }
    } else {
        Modifier
    },
)
```

Everything the card can otherwise do is gated on the same flag: `editable = !reordering` on the
inline text, no ⋯ menu, no `clickable` on the radio or the rating badge, and the text forced back
to its collapsed 3 lines. The creation field at the foot of the list is hidden too.

Visual lift while dragging:

```kotlin
Modifier
  .zIndex(if (isDragging) 1f else 0f)                     // above neighbours
  .offset { IntOffset(0, dragTranslationY.roundToInt()) } // follows the finger
```

`GoalTabContent` sets `userScrollEnabled = !optionsDragging` on the page's `LazyColumn` while a
card is held, so the vertical drag reorders instead of scrolling the page.

### Auto-scroll

Android auto-scrolls at the edges too, with the same 64dp zone and ramped speed. Two details are
worth knowing before touching it:

- **`userScrollEnabled = false` does not block it.** That flag gates the `scrollable` *gesture*
  only; `LazyListState.scrollBy` still moves the list, which is exactly what is wanted here.
- **It has to be a frame loop, not a drag callback.** A finger held still at the edge produces no
  pointer events, and that is precisely when the page must keep moving — so a `LaunchedEffect`
  keyed on `draggingId` runs `withFrameNanos { }` while a card is held. The card reports its
  finger position in **root coordinates** (`onGloballyPositioned` + the drag's local offset,
  accumulated by hand so it stays correct while only the page moves), and the loop folds whatever
  the list actually scrolled back into `onDragBy` — scrolling the page under a stationary finger is,
  to the slot maths, the same as moving the finger the other way.

The loop exists **only** while `draggingId != null`. That matters: a permanent `withFrameNanos`
subscriber never lets the test clock go idle, which is the failure mode that once hung the whole
visual-test suite (BUG-009).

State lives in `OptionsTabContent` (`order`, `draggingId`), re-seeded by a `LaunchedEffect` that
skips while `draggingId != null` — the same rule as the web. `onDragEnd` commits through
`GoalWorkspaceViewModel.reorderOptions(id, index)` → `ReorderOptionMutation`.

### Why the thresholds differ

Web measures the dragged card's own height once, at pointer-down, and caps the step. Android
measures **each neighbour's** real height (`onSizeChanged` into a `heights` map) and swaps as the
card clears it, so one drag can travel to any position with cards of different heights. Same idea,
different source of truth.

---

## Testing

| Surface | Coverage |
|---|---|
| **Web** | `e2e/options.spec.ts` → *"dragging a card downward reorders it (regression: down-drag froze)"* — a real Playwright mouse drag (`mouse.down` → stepped `mouse.move` → `mouse.up`) asserting the card ends up last. It is written as a **regression test for the pointer-capture bug**, so it deliberately drags **downwards**. |
| **Android** | `OptionsDragReorderTest` drives the real gesture under Robolectric. *"dragging the third option to the top"* — tap **Reorder**, then one continuous press-move-release, asserting it lands at index 0 (i.e. the drag can cross more than one slot). *"holding a dragged card at the bottom edge keeps scrolling the page"* — 9 options, a short drag into the edge zone, then the finger held still; it must still reach the last slot. `VisualCheckOptionsTabTest` renders the tab, the expanded long strategy, and reorder mode as PNGs — layout, not gesture. |

> **Testing the auto-scroll needs manual clock control.** `performTouchInput`'s `advanceEventTime`
> moves only the *input* clock and produces no Compose frames, so a `withFrameNanos` loop never
> ticks inside one injection block — the test silently measures finger travel alone. Set
> `compose.mainClock.autoAdvance = false`, split the gesture across `performTouchInput` calls
> (`down` / `moveBy` / `up` are one gesture per test), and pump `advanceTimeByFrame()` between
> them.

Manual edge cases worth re-checking after changes: rapid dragging and a failed mutation (the
optimistic update rolls back).

---

## Known limitations

- **Neither surface has a keyboard fallback.** The web's ↑/↓ nudge went away with the grip handle;
  a pointer/touch drag is currently the only way to reorder.

## References

- Web store action: `reorderOptions` in `src/lib/spira/store.ts`; API: `spiraApi.reorderOptions`.
- Android: `GoalWorkspaceViewModel.reorderOptions(id, index)` → `ReorderOptionMutation`.
- Backend: `reorderOptions(goalId: ID!, optionIds: [ID!]!)` in
  `backend/src/main/resources/graphql/schema.graphqls`, implemented in `GoalService.reorderOptions`
  (validates that the id list contains exactly the goal's options, then rewrites `position`).
- Backlog: **BUG-010** (the Android drag-and-drop request that this implements).
- Compose gestures: https://developer.android.com/jetpack/compose/gestures
