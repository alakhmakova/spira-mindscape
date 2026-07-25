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

### Gesture: a dedicated drag handle

The web uses an explicit **grip handle** (`GripVertical`, at the right edge of the card) rather
than long-press-anywhere. Reason: the card's text is an inline-editable field — a press-and-drag
starting on the text would fight with placing the caret and selecting text. A handle makes the
grab area unambiguous, works identically with mouse and touch, and is the conventional web
pattern. `touch-action: none` on the handle stops the browser from scrolling the page instead of
dragging.

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

### Keyboard accessibility

Pointer drags are inaccessible on their own, so the handle is a real `<button>`: focus it and
press **↑ / ↓** to move the card one slot (`nudge()`), which reuses the same `reorderOptions`
store action.

### Store / server

`reorderOptions(goalId, from, to)` in `src/lib/spira/store.ts` splices the array optimistically,
then (debounced) sends the full id list to `spiraApi.reorderOptions`, replacing the local list
with the server's response.

---

## Android

**File:** `android/app/src/main/java/com/spiramindscape/android/ui/goals/GoalWorkspaceScreen.kt`
(`OptionsTabContent` + `OptionCard`)

### Gesture: long-press then drag

On Android the whole card is draggable via `detectDragGesturesAfterLongPress` — there is no
separate handle. A long-press is the platform-native "pick this up" gesture and it does not
conflict with the short tap, which focuses the card's text for inline editing. Both gestures
coexist in the same modifier chain:

```kotlin
Modifier
  .pointerInput(option.id) {
      detectDragGesturesAfterLongPress(
          onDragStart = { onDragStart(); dragOffsetY = 0f },
          onDrag = { change, amount ->
              change.consume()
              dragOffsetY += amount.y
              val step = 72.dp.toPx()               // ≈ one card height
              while (dragOffsetY >= step) { onDragStep(false); dragOffsetY -= step }
              while (dragOffsetY <= -step) { onDragStep(true); dragOffsetY += step }
          },
          onDragEnd = { onDragEnd() },              // commits via the ViewModel
          onDragCancel = { onDragEnd() },
      )
  }
  .pointerInput(option.id) { detectTapGestures(onTap = { textFocusRequester.requestFocus() }) }
```

Visual lift while dragging:

```kotlin
Modifier
  .zIndex(if (isDragging) 1f else 0f)                 // above neighbours
  .offset { IntOffset(0, dragOffsetY.roundToInt()) }  // follows the finger
  .then(if (menuMode) Modifier else gestureMod)       // no dragging in menu mode
```

State lives in `OptionsTabContent` (`order`, `draggingId`), re-seeded by a `LaunchedEffect` that
skips while `draggingId != null` — the same rule as the web. `onDragEnd` commits through
`GoalWorkspaceViewModel.reorderOptions(id, index)` → `ReorderOptionMutation`.

### Why the thresholds differ

Web measures the real card height (`getBoundingClientRect()`), because web cards grow with their
text. Android uses a fixed `72.dp` step, matching its fixed-height card. Same idea, different
source of truth.

---

## Testing

| Surface | Coverage |
|---|---|
| **Web** | `e2e/options.spec.ts` → *"dragging a card downward reorders it (regression: down-drag froze)"* — a real Playwright mouse drag (`mouse.down` → stepped `mouse.move` → `mouse.up`) asserting the card ends up last. It is written as a **regression test for the pointer-capture bug**, so it deliberately drags **downwards**. |
| **Android** | `VisualCheckOptionsTabTest` renders the tab (static PNG) — it verifies layout, not the gesture. Drag itself is verified manually on device/emulator (long-press → drag → numbers update live → release → order persists across tab switches). |

Manual edge cases worth re-checking after changes: rapid dragging, dragging past the list edge
(no auto-scroll on either surface yet), and a failed mutation (the optimistic update rolls back).

---

## Known limitations

- **No auto-scroll** when dragging near the top/bottom edge of a long list (both surfaces).
- **Web drag needs the handle** — dragging the card body is intentionally not supported (it would
  conflict with inline text editing).
- **Android has no keyboard fallback** (the web has ↑/↓ on the handle).

## References

- Web store action: `reorderOptions` in `src/lib/spira/store.ts`; API: `spiraApi.reorderOptions`.
- Android: `GoalWorkspaceViewModel.reorderOptions(id, index)` → `ReorderOptionMutation`.
- Backend: `reorderOptions(goalId: ID!, optionIds: [ID!]!)` in
  `backend/src/main/resources/graphql/schema.graphqls`, implemented in `GoalService.reorderOptions`
  (validates that the id list contains exactly the goal's options, then rewrites `position`).
- Backlog: **BUG-010** (the Android drag-and-drop request that this implements).
- Compose gestures: https://developer.android.com/jetpack/compose/gestures
