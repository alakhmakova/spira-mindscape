# Android: goal workspace crashes ("Spira keeps stopping") — duplicate LazyColumn keys

- **ID:** BUG-004
- **Status:** ✅ Fixed (2026-07-16) — see Resolution. Pending redistribution + retest.
- **Reported by:** User (on-device, App Distribution build)
- **Area:** Android app — goal workspace (`ui/goals/GoalWorkspaceScreen.kt`)
- **Severity:** High (crashes the app on certain goals)

## Summary

Opening the goal workspace crashes intermittently ("Spira keeps stopping"). The user could view
some goals and mark a target done, but the app crashed periodically.

## Steps to reproduce

Open a goal whose **target, option, or resource share the same numeric id** (ids come from
different backend tables, so e.g. a target and an option can both be id `5`).

## Root cause

The workspace renders one `LazyColumn` containing targets, options, and resources with
`key = { it.id }` per section. Compose requires LazyColumn item keys to be **unique across the
entire list**, not per section. Since ids collide across entity types, two items could get the
same key → `IllegalArgumentException: Key "5" was already used...` → crash. It was intermittent
because it only triggers for goals whose cross-type ids happen to collide.

## Fix approach

Namespace the keys per entity type so they can't collide:
`key = { "target-${it.id}" }`, `"option-${it.id}"`, `"resource-${it.id}"`.

## How to verify fixed

- `GoalWorkspaceScreenTest` renders a goal whose target, option, and resource all share id `5`
  and asserts it displays without crashing (this test would crash on the old code).
- On device: open goals (including ones with many targets/options/resources) — no crash.

## Resolution

Keys in `GoalWorkspaceScreen`'s `LazyColumn` are now namespaced per type
(`target-`/`option-`/`resource-`). Added `GoalWorkspaceScreenTest` as a regression test
(colliding ids render fine). Full Android suite green (34 tests). Fixed build redistributed via
Firebase App Distribution for retest.
