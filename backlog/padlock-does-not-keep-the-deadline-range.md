# The padlock does not keep the deadline range, and does not say so

- **ID:** BUG-045
- **Status:** ✅ Fixed (2026-08-22) — see Resolution.
- **Reported by:** Owner (2026-08-22)
- **Area:** Web — `src/components/shell/shell-store.ts`; Android — `ui/goals/ViewPreferences.kt`,
  `ui/goals/GoalsDashboardScreen.kt`
- **Type:** Defect (UX / data loss)

## Summary

The owner set a **deadline range** and a sort on the goals list, closed the **padlock** to keep
them, left the app by a link and came back — and the range was gone, with the padlock still showing
closed.

Her words: "я указала deadline range и сортировку и нажала замок для сохранения в память. потом из
приложения перешла по сторонней ссылке и вернулась — фильтры были сброшены."

The padlock was working, for five of the seven questions the goals panel asks. The **two date
ranges were excluded by design** — and nothing on screen said so. The panel's own tooltip read
`"Kept until you unlock — survives a reload"` with no qualification, so the control promised the
whole arrangement and quietly delivered part of it.

## Steps to reproduce

1. Open the goals list (any width; reported on mobile web).
2. Filter glyph → **Filter & Sort**.
3. Pick a sort (e.g. **Title A-Z**) and a **Deadline range** From date.
4. Tap the **padlock** in the teal head so it closes. Tap **Apply**.
5. Navigate away to another site and come back.
6. Reopen the panel: the sort is still Title A-Z, the padlock is still closed — and the
   **From field is empty again**.

Reproduced on the running local stack with Playwright, at a Pixel 7 viewport and at 1280x900. The
`localStorage` written at step 4 contained no `deadlineFrom` key at all:

```json
{"sort":"title","sortDirection":"desc","confidence":"","status":"not-achieved",
 "goalDeadline":"all","locked":{"goals":true,...},"viewMode":"cards"}
```

## Root cause

The exclusion was explicit and lived in three places, all added 2026-08-21:

| Surface | Where | What |
|---|---|---|
| Web | `shell-store.ts` | `NEVER_PINNED = ["deadlineFrom","deadlineTo","targetDeadlineFrom","targetDeadlineTo"]`, filtered out of `PINNED`, so `partialize` never wrote them |
| Android, dashboard | `GoalsViewModel.kt` | the range lived in a `MutableStateFlow("")`; `GoalViewPreferences` had no key for it |
| Android, targets | `ViewPreferences.kt` | `deadlineFromState = mutableStateOf("")`, seeded empty and never written |

The reasoning at the time: a range is about a moment ("what is due this month"), so one closed over
in August would open the page in October on a list that looks empty for no visible reason.

That trade was decided the wrong way round. The cost it avoided is a stale range the user can see
and clear; the cost it created is a control that lies about what it keeps. And the worry itself was
already answered by two things that shipped in the same release: a list emptied by its own filter
now says so in a **warning notice** (`FilteredEmptyNotice`), and the trigger carries a **Guava dot**
whenever anything is narrowing the list — so a restored range is not invisible.

## Fix approach

**Pin the range like every other question** (owner's call, 2026-08-22).

- Web: `NEVER_PINNED` deleted; `PINNED = FIELDS`. The **search box** is still never pinned — it is
  not in `FIELDS` at all.
- Android: `deadline_from` / `deadline_to` keys added to `GoalViewPreferences` and
  `TargetViewPreferences`; `TargetViewState` seeds from them, writes through its setters, and
  carries them in `pinCurrent`; `GoalsDashboardScreen` gained a `pinDeadline()` that writes the
  deadline question and both ends together, since the three are one answer.
- Both surfaces: clearing the range has to reach storage as well — **Reset all** and choosing
  **No deadline** now write the cleared value, or the field would empty on screen and the old range
  would return on the next visit.

## How to verify fixed

- Web: the steps above — the From date survives leaving the app and coming back, on a phone
  viewport and on a desktop one. **Reset all** empties it in `localStorage` too.
- Android: same journey on the dashboard and inside a goal's targets.
- Tests: `shell-store.test.ts` (4 new cases) and the new
  `android/app/src/test/java/com/spiramindscape/android/ui/goals/ViewPreferencesTest.kt`.

## Resolution

Fixed 2026-08-22. The range is pinned on both surfaces, and clearing it is written through.

`ViewPreferences.kt` had **no test at all** before this — the padlock's store is the half of the
feature nobody can see, which is exactly how the exemption survived: every visible assertion was
green. `ViewPreferencesTest` now covers the contract end to end (10 cases): nothing written while
open, the whole arrangement written on close, later changes written too, opening clears the store,
the upgrade sweep, and the range specifically — read back through a **fresh instance over the same
prefs**, which is what the next launch does.

`CLAUDE.md` → Design → Components and chrome → 7 was updated: the "never pinned" list is now the
search box alone.
