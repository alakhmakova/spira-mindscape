# Fix plan — mobile design/parity issues found in testing (2026-07-16)

Bugs the user found testing the first full-CRUD build, with root cause + fix for each. Ordered by
theme. The guiding rule for text fields is `specs/tech-stack.md` → "Goal Page" → **inline
editing** (and the web's `InlineText` / `AutoTextarea` behavior in `src/components/spira/`).

---

## 1. Text fields — redesign ALL goal-page fields to true inline editing (HIGH)

**Problem:** goal title/description, target titles, reality items, options — all use a boxed
`OutlinedTextField` with a floating label (`ui/components/InlineComponents.kt` `EditableTextField`,
and `SpiraTextField` on the goal page). The spec requires **inline editing**: text that looks like
text and becomes editable in place, not form boxes. The user also reports goal title/description
**edits don't save** and the fields "look absolutely wrong."

**Web reference (`InlineText` in `src/components/spira/Inline.tsx`):** renders as plain text,
becomes editable in place, **commits on blur**, Enter commits/blurs, Escape reverts, and for
**required** fields keeps the last good value (won't save empty). `AutoTextarea` is the
multi-line variant (goal title/description) that auto-grows.

**Fix:**
- Replace `EditableTextField` with a real inline component `InlineEditText`:
  - `BasicTextField` styled as the surrounding text (no outline, no floating label, transparent
    background, inherits typography) — matches the web look.
  - Commit on **focus loss** and on the IME **Done** action; single-line and multi-line variants.
  - **Required** support: if emptied, revert to the last committed value (don't send empty) —
    e.g. goal title, target title, reality/option text.
  - Fix the save path: verify the commit fires and `updateGoal`/`setTargetTitle`/… actually
    persists (reproduce on the emulator + logcat). The likely miss is commit only on blur while
    focus never leaves; committing on Done + on focus-loss both fixes it.
- Apply it to **every** inline field on the goal page: goal **title** + **description** (multi-line,
  headline/body styles), **target title**, **reality** action/obstacle rows, **option** rows.
- Leave the **create/edit bottom-sheet forms** (`SpiraTextField`) as proper labelled inputs —
  those are forms, not inline edits (consistent with the web's create Sheets).

**Files:** `ui/components/InlineComponents.kt` (rewrite), `ui/goals/GoalWorkspaceScreen.kt`
(header, target card, reality, options use `InlineEditText`).

---

## 2. "Add goal" FAB — make it circular and stop it covering the last card (MEDIUM)

**Problem:** the FAB overlaps the Confidence area of the last goal card, and isn't round.

**Fix:** `ui/goals/GoalsDashboardScreen.kt` — set the FAB `shape = CircleShape`; add bottom
`contentPadding` to the goals `LazyColumn` (~96.dp) so the last card scrolls clear of the FAB.

---

## 3. Goal cards — circular progress ring on the left instead of a bar (MEDIUM)

**Problem:** cards show a linear progress bar; the user wants a **circular** progress indicator on
the **left** of each card.

**Fix:** `GoalsDashboardScreen.kt` `GoalCard` — lay out as a `Row`: left = a circular progress
ring (determinate `CircularProgressIndicator` or a `Canvas` arc) with the % in its center; right =
title + meta + confidence. Remove the linear bar from the card. Add a small reusable
`CircularProgress` to the kit.

---

## 4. Opening a goal flashes the loader twice (MEDIUM)

**Problem:** tapping a goal shows the spinner, it disappears, then reappears before the page — the
loader flashes twice.

**Root cause:** `GoalWorkspaceViewModel.init` calls `load()` (sets `Loading`); then the
`LifecycleResumeEffect` in `GoalWorkspaceRoute` fires on first resume and calls `load()` again,
which re-shows the full-screen spinner.

**Fix:** add a **silent** `refresh()` to `GoalWorkspaceViewModel` (fetch without setting
`Loading`, keep current content on failure — like `GoalsViewModel.refresh`). Use `load()` only for
the initial load / retry, and `refresh()` in the resume effect. Result: spinner shows once.

**Files:** `ui/goals/GoalWorkspaceViewModel.kt`, `GoalWorkspaceScreen.kt` (`GoalWorkspaceRoute`).

---

## 5. Numeric targets — several bugs (HIGH)

**5a. Can't create a numeric target without `start`.** The spec calls `start` optional (default 0),
but the backend **requires** it (`TargetService.validateNumericCreateInput`: *"Numeric target
requires start"*). The web always sends a start (defaulting to 0).
→ **Fix (client):** in `GoalWorkspaceViewModel.addTarget`, for `type == "numeric"` send
`start = start ?: 0.0` (default empty start to 0, matching the web). Keep the "Start (optional)"
label.

**5b. Pressing "−" at the start value reloads the page.** `current - 1` goes below `start`/0, the
server rejects it, `applyTargetUpdate` catches and calls `load()` → full reload.
→ **Fix (client):** clamp the new value to `[min(start,total), max(start,total)]` before calling
`onSetNumeric`; disable/no-op "−" at the minimum and "+" at the maximum so no invalid request is
sent.

**5c. Progress number isn't shown.** The card shows `current / total unit` but not the %.
→ **Fix:** show the percentage next to the numeric value (reuse the goal progress style).

**5d. Can't edit the value by tapping the number (only ±).** For big changes ± is too slow.
→ **Fix:** make the current value an inline-editable number (`InlineEditText`, number keyboard) →
`onSetNumeric(target.id, entered.coerceIn(min, max))`. Keep ± for small nudges.

**Files:** `GoalWorkspaceViewModel.kt` (addTarget default start; a `setNumericCurrent` that clamps
— or clamp in the card), `GoalWorkspaceScreen.kt` `TargetCard` numeric branch,
`ui/goals/NewTargetSheet.kt` (copy).

---

## 6. Checklist targets — no progress, can't add/edit tasks after creation (HIGH)

**Problem:** an existing checklist target shows items but **no progress**, and you can't **add /
edit / remove** its tasks (only toggle done).

**Fix:**
- Show the checklist progress bar + % in `TargetCard` (progress = done/total, already computed
  server-side and in `target.progress`).
- Add task management on an existing checklist target, sent via `setChecklistItems` (the whole
  items array — the backend reconciles by id):
  - **Add:** append an item with **no id** so the backend creates it. `ChecklistItemModel` has a
    non-null `id`; add a "new item" path so `setChecklistItems` sends `id = Optional.Absent` for
    new items (currently it always sends `id = present(it.id)`).
  - **Edit text / toggle / remove:** rebuild the items list and call `setChecklistItems`.
  - UI: an `InlineEditText` per item + delete, and an `AddItemRow` under the checklist (same
    pattern as reality/options).

**Files:** `data/goals/GoalsRepository.kt` (`setChecklistItems` — send absent id for new items;
maybe a small `ChecklistEdit` model), `ui/goals/GoalWorkspaceScreen.kt` `TargetCard` checklist
branch, `GoalWorkspaceViewModel.kt` (add/edit/remove checklist item helpers).

---

## 7. Can't edit target titles (HIGH)

Covered by #1 — the target title becomes an `InlineEditText` committing to `setTargetTitle`; verify
it persists on the emulator.

---

## Verification
- Emulator against a local backend: create a numeric target with only a target value (no start) →
  it's created with start 0; "−" at 0 does nothing (no reload); tap the number to set 50 directly;
  progress % updates. Create a checklist → add/edit/remove tasks on it later → progress updates.
  Edit goal title/description and target titles inline → values persist after leaving and
  reopening the goal. Open a goal → spinner shows once. Dashboard cards show a left circular ring;
  the FAB is round and never covers the last card.
- `:app:testDebugUnitTest` green; add/adjust tests for: numeric start-default on create, numeric
  clamp, checklist add/edit, inline required-field revert.
