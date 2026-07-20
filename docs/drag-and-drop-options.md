# Drag-and-Drop Reorder for Options Cards

## Overview

The Options screen (Goal Workspace, "Options" tab) allows users to long-press a card to drag it into a new position. As the card moves, it shuffles with neighbours in the list, and the "Option N" numbering updates automatically. Releasing the card commits the reorder via a GraphQL mutation.

## Where It Lives

**File**: `android/app/src/main/java/com/spiramindscape/android/ui/goals/GoalWorkspaceScreen.kt`

- `OptionsTabContent()` — manages the list, reorder state, and drag callbacks
- `OptionCard()` — individual card with drag gesture handler
- `OptionMenuBump()` — the white half-oval menu trigger (unrelated to drag, but visible during drag)

## How It Works

### State Management (OptionsTabContent)

```kotlin
@Composable
private fun OptionsTabContent(goal: GoalDetail, actions: GoalWorkspaceActions) {
    val sortedOptions = goal.options.sortedBy { it.position }

    // Reorderable list (local state during drag)
    var order by remember { mutableStateOf(sortedOptions.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }

    // Re-seed from source when data changes (add/remove/external refetch)
    LaunchedEffect(sortedOptions.map { it.id }) {
        if (draggingId == null) order = sortedOptions.map { it.id }
    }

    // ... rest of composable
}
```

**`order`**: A mutable list of option IDs representing the current reorder position. While the user drags, this list updates live, and the UI re-renders with the new layout.

**`draggingId`**: Tracks which card (by ID) is currently being dragged. Used to apply visual feedback (raised `zIndex`, `offset`). Cleared on drag end.

**`LaunchedEffect`**: Re-syncs `order` from the source `sortedOptions` whenever the source data changes, but only if no drag is currently in progress. This ensures external changes (add, remove, refetch from server) reset the local state.

### Per-Card Drag Listener (OptionCard)

Each card has two gesture handlers:

#### 1. Long-press + Drag (reorder)

```kotlin
val gestureMod = Modifier
    .pointerInput(option.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { 
                onDragStart()  // Signal OptionsTabContent that dragging started
                dragOffsetY = 0f 
            },
            onDrag = { change, amount ->
                change.consume()
                dragOffsetY += amount.y

                // Reorder threshold: swap with neighbour if finger travels ~72dp
                val step = 72.dp.toPx()
                while (dragOffsetY >= step) { 
                    onDragStep(false)  // Moved down, swap with next
                    dragOffsetY -= step 
                }
                while (dragOffsetY <= -step) { 
                    onDragStep(true)   // Moved up, swap with previous
                    dragOffsetY += step 
                }
            },
            onDragEnd = { onDragEnd() },  // Commit the reorder via GraphQL
            onDragCancel = { onDragEnd() },
        )
    }
```

**`detectDragGesturesAfterLongPress`**: Composite gesture — ignores short taps, but after holding for ~500ms, activates drag tracking.

**`dragOffsetY`**: Cumulative vertical movement of the finger during drag. Resets on drag start/end.

**`onDragStep(movedUp: Boolean)`**: Callback to shuffle the list. Called each time the finger travels ~72dp (roughly one card height). The list updates live, not at release.

#### 2. Short Tap (edit text)

```kotlin
.pointerInput(option.id) {
    detectTapGestures(onTap = { textFocusRequester.requestFocus() })
}
```

A short tap (without long-press) focuses the strategy text for inline editing. Both gestures coexist in the same modifier chain.

### Visual Feedback While Dragging

```kotlin
Box(
    Modifier
        .fillMaxWidth()
        .zIndex(if (isDragging) 1f else 0f)      // Raise above other cards
        .offset { IntOffset(0, dragOffsetY.roundToInt()) }  // Follow finger
        .then(if (menuMode) Modifier else gestureMod),  // Disable drag in menu mode
)
```

- **`zIndex`**: Raised card appears above neighbours so it doesn't disappear under them
- **`offset`**: Card visual position follows the finger (cumulative drag distance)
- **Border**: Card border changes colour to indicate it's selected/dragging (teal tint)

### Committing the Reorder (OptionsTabContent)

When `onDragEnd` is called:

```kotlin
onDragEnd = {
    val to = order.indexOf(opt.id)
    draggingId = null
    if (to != -1 && sortedOptions.getOrNull(to)?.id != opt.id) {
        actions.onReorderOption(opt.id, to)  // Existing ViewModel method
    }
}
```

Finds the final index of the dragged card in the reordered `order` list, then calls the existing
`GoalWorkspaceViewModel.reorderOption(id, index)` method, which:
1. Performs optimistic UI update in the ViewModel
2. Sends `ReorderOptionMutation` to the GraphQL API
3. Server persists the new position

The list re-seeds after the mutation completes (via `LaunchedEffect`), anchoring to the true source.

## Testing

### Static Render (VisualCheckOptionsTabTest)

```kotlin
@Test
fun optionsTabRenders() {
    compose.setContent { SpiraTheme { ... } }
    // Renders the initial state (cards in order, draggingId = null)
    compose.waitForIdle()
    saveWindow("options-tab")
}
```

Verifies that cards render, numbers are correct, and no crashes occur.

### On-Device Interactive Testing

1. Run the app on an emulator or device
2. Navigate to Goal Workspace → Options tab
3. Long-press a card and hold ~500ms
4. Drag the card up or down (move your finger ~72dp)
   - The card should shuffle with its neighbour
   - The "Option N" numbers should update live
5. Release the card
   - Numbers should be final (persist after commit)
   - Swipe to another tab and back → order should remain

### Manual Edge Cases

- **Rapid drag**: Drag fast; card should keep up with finger (no lag)
- **Drag near list edge**: Card should stay on screen, list should not scroll (no auto-scroll yet)
- **Tap to edit while dragging**: Not possible (short tap doesn't trigger during long-press)
- **Network error**: After releasing, if the mutation fails, the card reverts to its original position (the ViewModel's optimistic update rolls back)

## Key APIs

| API | Purpose | Docs |
|---|---|---|
| `detectDragGesturesAfterLongPress` | Composite gesture: long-press → drag tracking | [Compose Docs](https://developer.android.com/jetpack/compose/gestures#drag-and-drop) |
| `pointerInput` | Low-level pointer/touch event handler | [Compose Docs](https://developer.android.com/jetpack/compose/input) |
| `Modifier.offset` | Reposition composable by pixel offset | [Compose Docs](https://developer.android.com/jetpack/compose/layouts/basics#offset) |
| `Modifier.zIndex` | Control layering (stacking order) | [Compose Docs](https://developer.android.com/reference/kotlin/androidx/compose/ui/ZIndex) |
| `LaunchedEffect` | Re-synchronize state when dependencies change | [Compose Docs](https://developer.android.com/jetpack/compose/side-effects#launchedeffect) |

## Known Limitations / Future Work

- **No auto-scroll**: Dragging near the list edge doesn't scroll the list to reveal hidden options
- **Drag distance threshold fixed**: The ~72dp swapping threshold is not configurable
- **Single finger**: Multi-touch drag not supported (standard for lists)

## References

- Spira brand design: `CLAUDE.md` → Options cards (interaction) section
- ViewModel/mutation: `GoalWorkspaceViewModel.reorderOptions(id, index)`
- Compose gesture docs: https://developer.android.com/jetpack/compose/gestures
