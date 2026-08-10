# Options screen — visual redesign

**Status:** ✅ Fixed (2026-08-10) — see Resolution.

## Summary

The owner wants to **redesign the appearance of the Options screen** (the strategy cards on the
goal workspace's Options tab). This is an **enhancement / design change**, not a defect.

### Done (web — `OptionsList.tsx` + `Inline.tsx`)

- Rating smiley floated **top-right**; the **drag handle was removed**. Text wraps **under** the
  smiley (line 1 beside it, lines 2+ full-width below) via `InlineText`'s `floatTopRight` slot — no
  reserved empty right column.
- A strategy longer than **3 lines** collapses to 3 lines with "… **Show more**" on the 3rd line;
  expanded shows the full text ending with "**Show less**" (`InlineText` `clampLines`, float-safe
  `max-height` clamp).
- **Reorder mode:** a **"Reorder"** button toggles a mode where the **whole card** is the drag target
  (grab cursor) and all per-card actions are disabled (edit, rating, delete, select, Show more);
  reorders **save on each drop**; a **"Save"** button exits. Drag-reorder is possible only in this
  mode. Auto-scroll for long lists included (closes `long-option-cards-hard-to-drag.md`).

### Done (Android — `GoalWorkspaceScreen.kt`, 2026-08-10)

The owner asked for the Options screen and cards to be made **exactly like the web**, so Android's
own design was retired rather than adjusted:

- The teal full-screen page, the centered **"Option N"** cards, the **ACTIVE** band, the half-oval
  **bump** menu and the Guava corner-check ribbon are all gone. `NewOptionSheet.kt` and
  `OptionMenuSheet.kt` were deleted with them.
- A strategy is now the web's bordered row: 48dp active-radio cell, left-aligned inline text
  clamped to 3 lines behind Show more/less, the shared `ElementActionsMenu` (⋯ → Attach / Delete),
  and the **smiley rating badge** on the top-right edge.
- The ⋯ menu appears **only when the strategy text is tapped for editing** (the web reveals it with
  the caret; a phone has no hover), floating over the text's top-right corner. `InlineRichText`
  gained `onEditingChange` and `ElementActionsMenu` gained `onOpenChange` so the reveal survives
  the dropdown being open.

### Done (web — follow-up, 2026-08-10)

- The **Show more / Show less** toggle now matches Android: a worded link on its **own line** under
  the strategy, replacing the chevron that was pinned bottom-right on top of the clamped text.
- The **rating** needed a data change: `status` was never fetched on Android. Added to
  `GetGoal.graphql`, `OptionItem`, `GoalsRepository.setOptionStatus`, and
  `GoalWorkspaceViewModel.setOptionStatus` (optimistic).
- Adding a strategy moved from the "+" FAB + create sheet to the inline **"Add a strategy…"** field
  at the foot of the list; reordering moved from long-press-drag to the web's **Reorder / Save**
  mode.
- **Auto-scroll while dragging** was ported too, so the last cards of a long list are reachable in
  one drag (closes the Android half of `long-option-cards-hard-to-drag.md`).

## Resolution

Both surfaces now render one Options design, with the web as the spec. CLAUDE.md's "Options cards
(interaction)" rules and `docs/drag-and-drop-options.md` were rewritten to match; the old rules
(teal background, corner-check ribbon, bump menu, long-press drag) are explicitly retired there.

Verified by pixels per CLAUDE.md rule #4 — `VisualCheckOptionsTabTest` writes
`options-tab.png`, `options-tab-expanded.png` and `options-tab-reordering.png`, and
`OptionsDragReorderTest` covers both the multi-slot drag and the edge auto-scroll.
