# Deleting a goal on Android fails silently — no navigation, no message, no log

- **ID:** BUG-034
- **Status:** ✅ Fixed
- **Reported by:** Found while auditing logging (2026-08-07)
- **Area:** Android — goal workspace (`ui/goals/GoalWorkspaceViewModel.kt`)
- **Severity:** Medium (a destructive action appears to be ignored)

## Summary

On Android, tapping the X in the goal-workspace header and confirming the delete dialog did
**nothing at all** when the request failed: the app stayed on the goal, showed no message, and
wrote nothing to logcat or Crashlytics. From the user's side that is indistinguishable from a
dead button — and there was no way to tell afterwards whether the goal had been deleted.

`GoalsViewModel.createGoal` had the same shape: on failure the create sheet just stayed open with
no explanation.

## Steps to reproduce

1. Open a goal on the Android app.
2. Put the device in airplane mode (or stop the backend).
3. Tap the X in the header and confirm the delete.
4. Observe: nothing happens. No navigation, no error, no toast — and nothing in `adb logcat`.

## Root cause

`GoalWorkspaceViewModel.deleteGoal` caught the exception and did nothing with it:

```kotlin
} catch (e: Exception) {
    // Stay on the screen; nothing was deleted.
}
```

Staying on the screen is the correct *behaviour* — nothing was deleted, so navigating away would
be a lie. What was missing is telling the user (and us) that it failed. The same pattern existed
in `GoalsViewModel.createGoal`.

This is one instance of a wider pattern the logging audit found: ~30 `catch` blocks across the
Android ViewModels recovered silently, producing no signal on the device or in Crashlytics.

## Fix approach

1. Add a `_actionError: MutableStateFlow<String?>` to both view models, exposed as a `StateFlow`
   with a `clearActionError()`, set in the failing catch.
2. Render it with a new Spira kit component, `ui/components/SpiraInlineBanner.kt` (per CLAUDE.md
   rule #1 — no raw Material `Snackbar`). It sits under the chrome, above the content, so the goal
   stays visible and the user can retry. Themed from the semantic `error` ramp (Guava is the brand
   accent and must not double as a danger signal) and announced as a TalkBack live region.
3. Log the failure through `SpiraLog`, so it also becomes a Crashlytics non-fatal.

## How to verify fixed

- Automated: `GoalWorkspaceViewModelTest` — a failing delete sets `actionError` and does **not**
  invoke the `onDeleted` navigation callback, with the goal still on screen;
  `GoalsViewModelTest` — a failing create sets `actionError` and returns `creating` to `false`.
- Visual: `VisualCheckInlineBannerTest` renders the banner to
  `app/build/reports/visual/inline-banner.png` — checked by eye (wrapping text does not push the
  dismiss button off-screen).
- On-device: airplane mode → confirm a delete → the banner appears, the goal stays, and the
  failure shows up in Firebase → Crashlytics → **Non-fatals**.

## Resolution

Fixed **2026-08-07** as part of the logging work.

- `ui/goals/GoalWorkspaceViewModel.kt` — `actionError` state; `deleteGoal` sets
  `"Couldn't delete this goal. Please try again."` and logs `goal_delete_failed`.
- `ui/goals/GoalsViewModel.kt` — same for `createGoal` (`goal_create_failed`).
- `ui/components/SpiraInlineBanner.kt` — new kit component.
- `ui/goals/GoalWorkspaceScreen.kt`, `ui/goals/GoalsDashboardScreen.kt` — render it. Passed in as
  parameters so both screens stay stateless and renderable in a visual test.
- Tests as above, plus `VisualCheckInlineBannerTest`.

## Related

- `docs/logging.md` §8 — which Android call sites are worth logging and which are deliberately
  silent.
- `backlog/web-frontend-no-error-tracking.md` (BUG-005) — the same "failures leave no trace"
  problem on the web.
