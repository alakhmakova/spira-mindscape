# Android: two Reality visual tests assert UI text that no longer exists

- **ID:** BUG-014
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** Claude (while diagnosing why CI was red), confirmed against CI logs
- **Area:** Android tests — `app/src/test/java/com/spiramindscape/android/ui/VisualCheckRealityTabTest.kt`,
  `VisualCheckRealityDraftBlankTest.kt`
- **Severity:** Medium (keeps the Android CI job permanently red, hiding real regressions)

## Summary

The `Android tests` CI job failed on every run with `56 tests completed, 2 failed, 1 skipped`. The
same two tests failed every time, on `main` and on branches alike:

- `VisualCheckRealityDraftBlankTest > add new action opens a create form that can be cancelled`
- `VisualCheckRealityTabTest > reality tab shows the actions and obstacles toggle with marker icons`

These were **not** the flaky `AppNotIdleException` failures tracked in **BUG-009** — they were
deterministic `java.lang.AssertionError`s. The tests were simply **stale**: they drive UI text that
the app no longer renders.

## Steps to reproduce

1. `cd android && ./gradlew.bat :app:testDebugUnitTest --tests "*VisualCheckRealityTabTest"`
2. It fails at `VisualCheckRealityTabTest.kt:48` with `java.lang.AssertionError`.

## Root cause (confirmed)

The Reality tab used to render inline **"Add new action" / "Add new obstacle"** text rows. That was
replaced by a **Guava "+" FloatingActionButton** (`GoalWorkspaceScreen.kt`), which is **icon-only**
and exposes its label as a `contentDescription` that switches with the selected list:

```kotlin
Icon(SpiraIcons.Plus,
     contentDescription = if (realityKind == "obstacles") "Add obstacle" else "Add action")
```

The tests were never updated, so:

- `onNodeWithText("Add new obstacle").assertIsDisplayed()` — no such node exists → AssertionError.
- `onNodeWithText("Add new action").performClick()` — same, so the create sheet never opened.

`grep -rn "Add new obstacle" android/app/src/main/java/` returns **nothing**, which confirms the
strings are gone from the app.

## Fix approach

Address the FAB the way it is actually exposed — by `contentDescription` — instead of by text that
no longer exists. Keep the tests asserting the same user-visible behaviour (the add affordance is
present and opens a cancellable create form).

## How to verify fixed

1. `cd android && ./gradlew.bat :app:testDebugUnitTest --tests "*VisualCheckRealityTabTest" --tests "*VisualCheckRealityDraftBlankTest"` → both pass.
2. The full `Android tests` CI job reports `56 tests completed, 0 failed, 1 skipped` (the 1 skipped
   is the `@Ignore`d `VisualCheckRealityDraftSaveTest` from BUG-009).

## Resolution

Files changed:

- **`VisualCheckRealityTabTest.kt`** — the obstacles assertion now uses
  `onNodeWithContentDescription("Add obstacle").assertIsDisplayed()`, which also (usefully) pins the
  behaviour that the FAB **re-labels itself per Reality kind**.
- **`VisualCheckRealityDraftBlankTest.kt`** — opens the create sheet via
  `onNodeWithContentDescription("Add action").performClick()`. The rest of the test is unchanged and
  still verifies the sheet ("New action" title, "Add action" confirm) and that **Cancel** dismisses
  it without losing the existing item.

Both files gained the `onNodeWithContentDescription` import.

### Verification status (be aware)

- ✅ The test sources **compile** (`:app:compileDebugUnitTestKotlin` succeeds, 0 errors).
- ✅ The asserted strings **exist in the app**: `contentDescription = "Add action" / "Add obstacle"`
  (`GoalWorkspaceScreen.kt`), sheet `title = "New action"`, `confirmLabel = "Add action"`
  (`NewRealitySheet.kt`), and `"Cancel"` (`FormComponents.kt`). The old strings return **no** grep
  hits.
- ❌ **The two tests could not be executed locally.** On this Windows dev machine the
  `VisualCheck*` suite hangs indefinitely in `:app:testDebugUnitTest` — even a *single* class, and
  even before rendering its first frame (no result XML, no PNG). This is the environment problem
  CLAUDE.md already warns about ("the suite is SLOW and currently has a HANGING test"); the run was
  stopped with `./gradlew.bat --stop`. **They therefore get their real verification from the CI
  Android job**, which runs this suite in seconds on Linux.
- ⚠️ Risk considered: `VisualCheckRealityDraftBlankTest` now actually **opens** the create
  `ModalBottomSheet` (before the fix it never did, because the click target didn't exist). BUG-009's
  unresolved hang is specifically `performTextInput` **inside** a `ModalBottomSheet`; this test only
  opens the sheet and clicks **Cancel**, and `VisualCheckOptionMenuSheetTest` already opens a sheet
  and passes in CI — so the risk is low. If the Android job ever *hangs* rather than fails after
  this change, this is the first place to look.

**Related:** BUG-009 (the genuinely flaky `AppNotIdleException` / hang in the visual suite) is a
separate, still-open issue — the `@Ignore`d `VisualCheckRealityDraftSaveTest` remains the 1 skipped
test, and the local-hang symptom above is the same family.
