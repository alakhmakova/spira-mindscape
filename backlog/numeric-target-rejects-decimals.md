# Numeric target rejects decimal values with a misleading error

- **ID:** BUG-020
- **Status:** ✅ Fixed — see Resolution. Pending manual commit by the user.
- **Reported by:** User
- **Area:** Frontend web — numeric target editing (`src/components/spira/Targets.tsx`)
- **Severity:** Medium (valid input blocked; the error text is also wrong/confusing)

## Summary

For a **numeric** target, typing a decimal value (e.g. `1.1`) into the inline current / total /
start field is rejected with the message *"Enter a non-negative whole number."* — which the user
read as "can't enter a negative number", i.e. the message doesn't match what actually happened. The
value isn't negative; it's a decimal, and decimals should be allowed. The same integer-only
restriction was also on the create-target form.

The backend already stores these as `Float` (GraphQL `Float`, DB `DOUBLE PRECISION`), so the limit
was purely a frontend validation/parsing artifact.

## Steps to reproduce

1. Open a goal → add or open a **numeric** target.
2. Click the inline current (or total, or start) value and type `1.1`, then blur/Enter.
3. Observe: the value reverts and *"Enter a non-negative whole number."* appears.
   (Same on the create-target sheet's Start/Target fields.)

**Expected:** `1.1` is accepted; the ± buttons still step by whole units.
**Actual:** decimals are rejected with a message that reads as if the input was negative.

## Root cause (confirmed)

`Targets.tsx` gated numeric input to integers in three places:

- `InlineEditable` (inline goal-page editing): validated with `/^\d+$/` and, on failure, showed
  *"Enter a non-negative whole number."*; committed values were parsed with `parseInt(v, 10)`,
  which also truncates decimals.
- The create-target form: `/^\d+$/` on start/total with message *"…must be non-negative whole
  numbers."*, and `<Input type="number" step={1}>`.

A decimal like `1.1` fails `/^\d+$/`, so it never reached the store, and the message wrongly
implied a sign problem.

## Fix approach

Allow hand-typed non-negative decimals while keeping the ± steppers at whole units (backend already
accepts floats):

- Inline: validate with `/^\d+(\.\d+)?$/`, parse with `parseFloat`, and reword the message to
  *"Enter a non-negative number."*
- Create form: same regex, message *"Start and target must be non-negative numbers."*, and
  `step="any"` on the number inputs so the browser doesn't flag a decimal as a step mismatch.
- The ± buttons keep `current ± 1` (whole-unit stepping), as requested.

Decimal separator is the dot `.` (matches the rest of the app and `parseFloat`); comma input is out
of scope.

## How to verify fixed

1. Numeric target → type `1.1` into current/total/start inline → it saves, no error.
2. The ± buttons still move by 1 (e.g. `1.1` → `2.1`).
3. Create-target sheet accepts `1.5` for Start/Target.
4. Negative or non-numeric input is still rejected, now with *"…non-negative number."*
5. `npm test`, `tsc`, lint all clean.

## Resolution

Fixed in `src/components/spira/Targets.tsx`:
- `InlineEditable` numeric validation `/^\d+$/` → `/^\d+(\.\d+)?$/`; message "…whole number." →
  "Enter a non-negative number."; the three inline commits `parseInt(v, 10)` → `parseFloat(v)`
  (current / total / start).
- Create form: start/total regex → `/^\d+(\.\d+)?$/`; message → "Start and target must be
  non-negative numbers."; both numeric `<Input>`s `step={1}` → `step="any"`.
- The ± steppers are unchanged (still whole-unit).

Verification: `npm test` green; `tsc --noEmit` clean; `Targets.tsx` lint clean. No tests asserted
the old integer-only behavior.

## Android parity — done

Android already parsed decimals via `toDoubleOrNull()`, but the fields used `KeyboardType.Number`,
whose soft keyboard doesn't surface a decimal point. Switched to `KeyboardType.Decimal` so a
fractional value can be typed, in both the create sheet (`NewTargetSheet.kt` — Start + Target) and
inline numeric editing (`GoalWorkspaceScreen.kt`). Parsing and the ± steppers (whole-unit,
`coerceIn`) are unchanged. `:app:assembleDebug` succeeds; distributed to testers via Firebase App
Distribution.
