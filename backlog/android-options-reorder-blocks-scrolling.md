# Android: reorder mode makes the Options list almost impossible to scroll

- **ID:** BUG-038
- **Status:** ✅ Fixed (2026-08-18) — see Resolution.
- **Reported by:** User (2026-08-18, `GRO-137`)
- **Area:** Android — `ui/goals/GoalWorkspaceScreen.kt` (`OptionsTabContent`, `OptionCard`)
- **Type:** Defect (UX)

## Summary

With **Reorder** turned on, scrolling the Options page is a fight. A swipe meant to move the page
picks a card up and starts dragging it instead, so reaching an option below the fold — the whole
reason someone turns reorder on — takes several attempts.

The owner's words: "при нажатии на Reorder сейчас происходит мешанина при попытке скролла — очень
тяжело попасть в скролл, вместо этого цепляются карточки для drag and drop … ведь скролл это свайп,
а драг энд дроп требует нажатия — они не должны смешиваться?"

## Steps to reproduce

1. Open a goal → **Options**, with enough options that the list scrolls.
2. Tap **Reorder**.
3. Swipe up to scroll the list.
4. The card under the finger is dragged instead of the page scrolling.

## Root cause

In reorder mode the **whole card** was the drag target:

```kotlin
Modifier.pointerInput(option.id) { detectDragGestures(...) }
```

`detectDragGestures` claims the gesture on the **first movement**, before the parent `LazyColumn`
can. And because a drag was in progress the list's own scroll was then frozen deliberately
(`onDraggingChange(true)`), so nothing else could move either. Two different gestures — a swipe and
a drag — were sharing one target, and the one that fired first always won.

## Fix approach

Give each gesture its own trigger, which is exactly the distinction the owner drew:

- **A handle in the left cell, standing where the radio button is**, visible only in reorder mode.
  A touch that lands there starts the drag straight away.
- **The rest of the card scrolls the page**, in reorder mode as everywhere else.
- **Press and hold anywhere on a card** also starts a drag
  (`detectDragGesturesAfterLongPress`), for the finger that is already on the card. A long press
  cannot be confused with a swipe: a swipe moves before the press timer elapses and falls through
  to the list.

## How to verify fixed

- Options tab with several long options → **Reorder** → a plain swipe scrolls the page normally.
- Dragging the grip in a card's left cell moves the card, and the page auto-scrolls when the finger
  nears an edge.
- The active option is still recognisable in reorder mode: teal cell, teal card border, teal grip.
- Press-and-hold on a card, then drag: also moves the card.
- Leaving reorder mode restores every per-card control (radio, badge, ⋯ menu, inline editing).

## Resolution

**2026-08-18 — fixed.** `OptionCard` now draws `OptionDragHandle` — Gravity's **`dots-9`** grip in
the border grey, in a 24dp touch strip — whose `pointerInput` runs `detectDragGestures` immediately.
The card-level gesture changed from `detectDragGestures` to `detectDragGesturesAfterLongPress`, so
an ordinary swipe now reaches the `LazyColumn`.

**Where it sits took three goes, so don't move it again without asking.** It shipped first as a
full-width band across the top of the card on the pale teal wash, which meant the card's chrome had
to move from the `Row` to a `Column` wrapping it. The owner rejected that on sight: the band lay
over the **radio cell's column**, cutting the card's left edge in two, and a list of them read as a
row of headers. Moving it into the content column above the words was rejected too.

It now stands **in the left cell, exactly where the radio button is** — the same cell, the same
width, and the card's shape does not change at all between the two modes; only what that one cell
holds does. The cell keeps its teal wash and the card its teal border for the **active** option, and
the grip takes the teal too, so which option is active stays visible while the list is being
rearranged. The card is a plain `Row` again.

The reorder hint below the toolbar says what the gesture is now — "Drag a card by the handle at its
top. Swiping still scrolls the page." — and is drawn as the app's `Info` notice card rather than as
a grey line.

Related: `long-option-cards-hard-to-drag.md` (the web's version of the "can't reach the target
position" problem, fixed separately with collapse-while-dragging and auto-scroll).
