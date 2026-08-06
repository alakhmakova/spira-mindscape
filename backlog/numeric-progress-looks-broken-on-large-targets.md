# A numeric target's progress looks broken on large numbers (frozen at 0 %, then stuck on 1 %)

- **ID:** BUG-028
- **Status:** ✅ Fixed (2026-08-03)
- **Reported by:** User
- **Area:** Web frontend — progress display (`src/lib/spira/progress.ts`,
  `src/components/spira/Targets.tsx`, `GoalCard.tsx`, `GoalsTable.tsx`,
  `src/routes/goals.$goalId.tsx`)
- **Severity:** Medium (nothing is lost, but real progress is invisible — the app reads as broken)

## Summary

On a numeric target with a large total — 1 900 000 SEK — the percentage was useless in two
different ways, reported one after the other:

1. **Stuck at 0 %.** Weeks of saving showed a flat `0%`, because every value below 5 000 SEK
   rounded to zero.
2. **Stuck at 1 %.** After the first fix, 10 000 SEK and 20 000 SEK *both* printed `1%` — the
   fix only added decimals where rounding produced 0 or 100, so everything in between was still
   collapsed to whole percent.

Alongside these, a third complaint from the same screen: **the bar did not move while typing.**
Editing the current value inline changed nothing until focus left the field ("кажется, что
прогресс не работает").

## Steps to reproduce (before the fix)

1. Create a numeric target: start `0`, target `1900000`, unit `SEK`.
2. Set the current value to `4000` → the card reads `0% progress`.
3. Set it to `10000`, then `20000` → both read `1% progress`.
4. Type a new value into the inline number without leaving the field → the bar and the percentage
   stay where they were until you tab away or press Enter.

## Root cause

- **Rounding was blind to the target's scale.** Every percentage went through
  `Math.round(progress * 100)`, which is right for a four-task checklist (25 % steps) and wrong for
  a target with 1 900 000 increments, where a whole percent is 19 000 SEK. The first fix keyed the
  precision off the *percentage value* (only rescuing a rounded 0 or 100) instead of off the
  *target's resolution*, which is why 10 000 and 20 000 still collided.
- **The inline editor commits on blur/Enter** — correct by design (CLAUDE.md: inline fields must
  never write on every keystroke) — but nothing showed the typed value in the meantime, so the
  display had no way to move until the commit.

## Fix

**Precision follows the target, not the number** — `src/lib/spira/progress.ts`:

- `progressSteps(target)` — how many increments the progress actually has: `|total − start|` for
  numeric, `items.length` for a checklist, `1` for binary. `goalProgressSteps(goal)` does the same
  for a goal: its progress is the mean over targets, so it is the finest target's steps multiplied
  by the number of targets.
- `formatPercent(fraction, steps)` — one step is worth `100 / steps` percent; below 0.1 % it
  prints two decimals, below 1 % one, otherwise none. Trailing zeros are trimmed, so a checklist
  still reads `50 %` and not `50.00 %`. Whatever the precision, a genuine 0 and a genuine 1 are
  the only values allowed to print as `0` and `100` — anything else that would round to them
  escalates precision, then falls back to `<0.01` / `>99.99`.
- Every percentage in the app passes its resolution: the "Will do" table, the numeric editor, the
  mobile card's band, the goal page's Progress KPI, the dashboard cards and the goals table.

| Case | Before | After |
|---|---|---|
| 4 000 / 1 900 000 | `0%` | `0.21%` |
| 10 000 / 1 900 000 | `1%` | `0.53%` |
| 20 000 / 1 900 000 | `1%` | `1.05%` |
| 950 000 / 1 900 000 | `50%` | `50%` |
| 2 of 4 tasks | `50%` | `50%` |
| 1 899 999 / 1 900 000 | `100%` (untrue) | `>99.99%` |

**The bar moves as you type** — `Targets.tsx`:

- `InlineEditable` gained `onTyping`, fired per keystroke for *display only*; the value still
  commits on blur/Enter, so the store-write rule is untouched.
- `NumericBody` keeps a preview progress computed from the typed text through the existing
  `targetProgress` (no duplicated formula), and shows it on the bar and the percentage. Invalid,
  empty or out-of-range input previews nothing rather than flashing nonsense; a locked target
  previews nothing at all. Focus leaving the editors clears the preview — by then the value has
  either committed or reverted.
- The preview is handed up to the enclosing mobile card (`onPreviewProgress`) so the card's band
  and the bar inside it can never disagree mid-edit.

## How to verify fixed

1. Numeric target `0 → 1 900 000 SEK`: set 10 000 → `0.53% progress`; set 20 000 → `1.05%`.
2. A four-task checklist next to it still shows whole percent (`25 / 50 / 75 %`).
3. Type into the current value **without leaving the field**: after `4` the percentage reads
   `<0.01%`, after `400000` it reads `21%`, and the card's band matches. Pressing Enter keeps the
   same number — no snap-back.
4. An unfinished target never displays `100%`, and a target with any progress never displays `0%`.

## Resolution

Fixed on 2026-08-03 in `src/lib/spira/progress.ts` (new `progressSteps`, `goalProgressSteps`,
rewritten `formatPercent`), `src/components/spira/Targets.tsx` (`onTyping` + the preview),
and the four call sites that render a percentage.

Covered by unit tests in `src/lib/spira/progress.test.ts` — the reported 10 000 vs 20 000 case,
coarse targets staying whole, trailing-zero trimming, the 0/100 guards, and `progressSteps` /
`goalProgressSteps`. Verified in the browser on a real 1 900 000 SEK target, including typing
digit by digit without leaving the field.

Carried to Android on 2026-08-04: `android/.../ui/util/Progress.kt` is a line-for-line port, used
by the target card's footer and numeric editor and by the goal header's progress figure, with the
same cases asserted in `ui/util/ProgressTest.kt`. The typing preview came across too — the card's
percentage and the bar both follow what is being typed, while the value still commits on blur/Done.
