# One set of filters is shared by every goal, padlock included

- **ID:** BUG-067
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-31 — "Фильтры применяются одни и те же ко всем goal — это
  неправильно, у каждой goal должна быть своя память о фильтрах для всех частей goal, в том числе
  о замочке. Например я применила фильтры для targets внутри goal 1 и поставила замочек. Открываю
  другую goal — там те же самые фильтры стоят, хотя замочек открыт. Я изменила в goal 2 так как мне
  нужно, вернулась в goal 1 и там тоже всё сбросилось на новые изменения, хотя стоит замочек."
- **Area:** Web (`src/components/shell/shell-store.ts`) + Android
  (`ui/goals/ViewPreferences.kt`) — both surfaces
- **Severity:** High — the padlock is a promise the app could not keep. Another screen wrote
  straight through a closed one, and nothing on screen admitted it.

## Summary

`targets`, `options` and `resources` are lists **inside a goal**, but both surfaces kept exactly
one set of answers for each of them, shared by every goal. Two faults followed, and the second is
the worse:

1. **A filter set on goal 1 was already on when goal 2 opened** — with goal 2's padlock sitting
   open, which is supposed to mean "this list keeps nothing".
2. **Changing it on goal 2 rewrote goal 1's arrangement behind goal 1's closed padlock.** A lock
   another screen can write through is not a lock.

The All-goals dashboard's own list is genuinely app-wide and was never part of the defect.

## Steps to reproduce

1. Open goal 1 → Will do → Filter & Sort. Set Progress = Done and **close the padlock**. Apply.
2. Open goal 2 → Will do. Its targets are already filtered to Done, and its padlock is open.
3. Change goal 2's filter — say Type = Checklist.
4. Go back to goal 1. Its filter is now Checklist, and its padlock is still closed.

Reproduced in a real browser at 1568×744 (localhost, `local` profile) and on the `spira_pixel`
emulator with `:app:installDev`, on 2026-08-31.

## Root cause

Both stores were flat.

- **Web** — `shell-store.ts` held every list's answers as top-level fields (`targetStatus`,
  `optionLean`, `resourceSort`, …) with one `locked: Record<ListKey, boolean>` beside them. There
  was no goal id anywhere in the store or in `localStorage`.
- **Android** — each list had one `SharedPreferences` file with unprefixed keys (`filter`, `sort`,
  `locked`), so the second goal to open simply read and overwrote the first goal's values.

## Fix approach

Scope the three goal-owned lists by goal id on both surfaces; leave the dashboard's list app-wide.

- **Web** — the store now keys everything by a **scope**: `goals` for the dashboard,
  `targets:<goalId>` / `options:<goalId>` / `resources:<goalId>` for a goal's own lists.
  `setView`, `resetList`, `setLocked` and the read hooks all take the goal id, and `GOALS_SCOPE`
  (a named `null`) is what an app-wide call site passes — so "I had no goal id to hand" cannot be
  mistaken for "this list is app-wide". A field's key says which list it belongs to
  (`LIST_OF_FIELD`), so a caller never repeats it. `partialize` writes only the scopes whose
  padlock is closed, so a goal that was merely visited leaves nothing behind.
- **Android** — `LockablePreferences` takes a `namespace` and prefixes **every** key it touches,
  the padlock included. Crucially `clearOwn()` removes only its own prefix, so opening one goal's
  padlock cannot clear another's. `remember*ViewState(goalId)` is keyed on the goal, so switching
  goals reseeds from that goal's store.
- **A goal-less caller gets `NO_GOAL`** on both surfaces, normalised in Android's *constructor* so a
  store built directly cannot skip it. This is a **legibility** guard, not an isolation one: no goal
  has a blank id, so a blank one could never have collided with a real goal's answers whatever the
  fallback. The name is what matters — `targets:none` and `none.filter` say "something wrote without
  a goal", where a bare `targets:` reads as the shared bucket this change removes.
- **Migration, and it is not one rule but two.** A **goal-owned** list's v1 arrangement is one
  global answer that cannot honestly be attributed to any single goal, so it is **dropped**: those
  lists open on their defaults with every padlock open. The **All-goals** list is *adopted* instead
  — it was never part of the defect, there is only one of it, and its old keys mean exactly what
  they mean now. The web's `migrate` lifts the flat fields into `views.goals`; Android's schema bump
  **renames** `spira_goal_view`'s keys into `app.*` rather than clearing them. Dropping a dashboard
  arrangement the user had pinned would be the failure the padlock spec names, inflicted by the fix
  for a different list. `viewMode` is carried across on the web too — it is not a filter and no
  padlock covers it.

## Known gap left open

**A deleted goal's scope is never pruned**, on either surface: its `views["targets:<id>"]` /
`locked["targets:<id>"]` stay in `localStorage`, and `<id>.*` keys stay in the Android preferences
files. Not a correctness fault, and bounded for live goals by the fifty-goal cap, but deleted ones
accumulate with nothing reclaiming them. Worth a sweep keyed off `deleteGoal`
(`src/lib/spira/store.ts`) if it ever grows enough to matter.

## How to verify fixed

**The journey, through the real UI** — the owner's own scenario, and the only level that proves the
*screen* hands its goal's id to the store on the write as well as on the read. A store scoped
perfectly behind a component still reading one global field would pass every store test in the repo.

- `npx playwright test e2e/per-goal-filters.spec.ts` — five tests, run in order against three goals:
  a pinned filter on A leaves B untouched; another goal cannot write through A's closed padlock; two
  padlocked goals hold different arrangements at once; a goal whose padlock is open keeps nothing
  across a reload; and none of it disturbs the two padlocked goals, across a reload.
- `cd android && ./gradlew.bat :app:testDebugUnitTest --tests "*PerGoalFiltersTest"` — the same
  journey in one Compose test, switching goals by swapping the state the one composition renders,
  which is what navigation does. **`testDebugUnitTest`, not `testDevUnitTest`**: Robolectric cannot
  resolve `ComponentActivity` under the `dev` build type, and every existing Compose UI test in the
  repo fails there the same way.

**The stores**

- `npx vitest run src/components/shell/shell-store.test.ts` — 31 cases: the "one goal's lists are
  its own" block, the migration split (a goal list dropped, the All-goals list carried), and the
  goal-less scope.
- `./gradlew.bat :app:testDebugUnitTest --tests "*ViewPreferencesTest"` — 17 cases, the same ground.

## Resolution

Fixed 2026-08-31. Both stores are scoped per goal; the dashboard's list is unchanged.

Verified against the defect, not only for it: reverting the scoping (`scopeKey` returning the bare
list on the web, `key()` ignoring the namespace on Android) turns **8** web cases and **4** Android
cases red, and each goes green again with the fix. Then confirmed end to end — in Chrome at
1568×744, where `localStorage` reads
`{"views":{"targets:2858":{"targetStatus":"done"}},"locked":{"targets:2858":true}}` and goal 2859
opens unfiltered; and on the emulator, where `spira_target_view.xml` holds only `2859.*` keys and
goal 2858's unlocked change writes nothing at all.

The whole Playwright suite (34) and the frontend unit suite (264) pass unchanged.

**The journey tests came last, and they are the ones that would have caught this.** The store
tests were written first and pin the store only; the owner asked for the scenario she actually
walked, on both surfaces, and it is a stronger test for a reason worth keeping: it exercises the
join between the screen and the store, which is where a scoping bug of this shape can hide from
every unit test. Checked red the same way — with the scoping reverted, the Playwright run fails at
step 2 ("goal B shows goal A's filter") and again at step 3 (goal B's padlock is already closed,
inherited from A), and the Android test fails with `gB: its target should be listed`.

A `/code-review` pass afterwards caught two things worth recording, both since fixed: the All-goals
list was being dropped by a migration meant for a defect it had no part in, and the file's own
explanation of the empty-namespace guard was **factually wrong** — it claimed an empty prefix would
clear every goal's keys, which the always-prefix form makes impossible. That wrong sentence had
already been copied into `CLAUDE.md` and into this file. Both are corrected; the guard stays,
described as what it actually is.
