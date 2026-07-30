# Options screen — visual redesign (in progress)

**Status:** 🔧 In progress (first pass done; owner may specify more)

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

Owner may still describe further redesign; capture and continue here. Android parity is separate.

## What we know so far

- Applies to the Options tab strategy cards (web: `src/components/spira/OptionsList.tsx`; Android has
  its own Options card design — see CLAUDE.md "Options cards" rules and
  `docs/drag-and-drop-options.md`).
- Related open item: long option cards are hard to drag (`long-option-cards-hard-to-drag.md`); a
  redesign may change or supersede the drag interaction.
- Any redesign must still honor the brand + UI rules in CLAUDE.md (teal Options background, white
  cards, Guava corner-check for the active option, Lucide icons, no emoji, etc.).

## Next step

Owner to describe the desired look/behavior; then plan and implement (web first, then Android
parity), verifying against pixels per CLAUDE.md rule #4.
