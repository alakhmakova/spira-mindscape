# A target that was unlocked once never auto-locks again at 100%

- **ID:** BUG-031
- **Status:** ✅ Fixed (2026-08-08) — both surfaces; pending manual commit by the user
- **Reported by:** User (2026-08-07, testing on Android)
- **Area:** Progress lock — Android (`ui/util/Progress.kt`,
  `ui/goals/GoalWorkspaceViewModel.kt`) and web (`src/lib/spira/progress.ts`)
- **Type:** Defect

## Summary

Reaching 100% is supposed to **pin a target's progress** so a stray tap can't undo finished work.
It only does so **the first time**. Once the user has toggled the padlock off, the target never
locks itself again, however many times it is completed afterwards.

## Steps to reproduce

1. Open a goal with a target that is not yet complete.
2. Tap the padlock to **unlock** it (this writes an explicit `progressLocked = false`).
3. Bring the target to **100%** (tick the binary toggle / finish the checklist / reach the number).
4. Expected: the target locks itself. Actual: it stays unlocked, and stays unlocked forever after.

## Root cause

The lock was **derived state with a fallback**, not an event:

```kotlin
fun isProgressLocked(target: TargetItem): Boolean =
    target.progressLocked ?: (target.progress >= 1f)
```

(the web is identical: `t.progressLocked ?? targetProgress(t) >= 1`).

The `>= 1f` branch is only consulted while `progressLocked` is **unset**. The moment the user
toggles the padlock, the field holds a real `false`, which wins over the fallback permanently —
so the explicit unlock outlives the completion it was meant for. The "auto-lock at 100%" was
never a rule about being complete; it was only a *default for targets the user had never
touched*.

## Fix approach

Treat auto-lock as an **event on crossing into 100%**, not as a fallback value: when an update
takes a target from below 100% to 100% and the flag currently holds an explicit `false`, write
`progressLocked = true`. An unset flag needs no write — it already locks itself.

Unlocking a target that is *already* at 100% must still stick, so the user can correct a finished
target: only the **transition** re-locks, not merely being complete.

## How to verify fixed

- The reproduction above ends with the target locked.
- Unlocking an already-complete target and editing it still works — it does not re-lock under the
  user's hands.
- An update that doesn't complete the target leaves an explicit unlock alone.
- Unit tests in `GoalWorkspaceViewModelTest` cover both the re-lock and the leave-alone case.

## Resolution

Fixed on both surfaces by making the re-lock an **event on crossing into 100%**:

- **Android** — `GoalWorkspaceViewModel.applyTargetUpdate` compares the target before and after a
  change and writes `progressLocked = true` when the update completes a target whose flag holds an
  explicit `false`. Two unit tests in `GoalWorkspaceViewModelTest` cover the re-lock and the
  leave-alone case.
- **Web** — `relockOnCompletion` in `src/lib/spira/progress.ts`, applied in the store's
  `updateTarget` optimistic patch. It rides along with the same mutation, so no extra request is
  needed. Four tests in `progress.test.ts` cover re-locking, an update that doesn't complete the
  target, an unlock on an already-complete target (must stick), and an unset flag (no write —
  100% already locks itself).

`isProgressLocked` keeps its original meaning on both surfaces: it is the *reading* rule, and the
re-lock is deliberately separate from it.
