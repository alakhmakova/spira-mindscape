# Cross-device data does not refresh without reload / re-login

- **ID:** BUG-001
- **Status:** ✅ Fixed (2026-07-15) — see Resolution. Pending manual commit by the user.
- **Reported by:** User
- **Area:** Frontend web app — data sync (`src/lib/spira/store.ts`, `src/components/shell/AppShell.tsx`)
- **Severity:** High (data looks stale / inconsistent across devices)

## Summary

When a signed-in user changes data on one device (e.g. desktop), the change does not
appear on another already-open surface (e.g. the phone / responsive web) until the page is
fully reloaded. Signing out and back in "fixes" it only because that reloads the page. It
feels like two separate sessions, but it is not.

## Steps to reproduce

1. Sign in as the same user on two surfaces at once: desktop browser **and** a phone
   browser (or two tabs).
2. On desktop, edit a goal (rename it, change a target's progress, etc.).
3. Look at the phone / other tab **without reloading it**.

**Expected:** the other surface reflects the change within a few seconds.
**Actual:** the other surface keeps showing the old data indefinitely until a full reload
or re-login.

## Root cause (confirmed)

It is **not** an auth/session problem and **not** two separate sessions. Backend sessions
are server-side in PostgreSQL (`spring_session`) and all data is per-user and centralized —
the desktop edit **is** saved to the shared database immediately.

The web client simply **fetches goals once per page load and never re-fetches**:

- `src/lib/spira/store.ts` → `loadGoals()` starts with
  `if (get().isLoading || get().hasLoaded) return;`, so once `hasLoaded` is `true` it never
  loads again.
- `src/components/shell/AppShell.tsx` calls `loadGoals()` a single time on mount.
- The only runtime listeners are `offline` / `online` — there is **no** refetch on tab
  focus, visibility change, or app resume.

So each surface shows the in-memory snapshot captured at its initial load. A reload (which a
re-login triggers) is the only thing that pulls fresh data. `refreshGoals()` already exists
and force-refetches; it was just never triggered on return-to-app.

## Fix approach

"Refetch on return": call the existing `refreshGoals()` when the tab/app regains focus or
visibility (`visibilitychange`, `focus`, `pageshow`), plus a light poll (~45–60s) while the
tab is visible. Guard so a refresh does not clobber an optimistic edit whose debounced write
is still in flight. No backend change required. (Realtime SSE push was considered and
deferred — see `specs/2026-07-15-native-mobile-app/plan.md`, Part 1.)

## How to verify fixed

1. Reproduce the steps above with the fix in place.
2. On the second surface, after returning focus (switch tabs / unlock phone), the change
   appears within a second; side-by-side surfaces update within the poll interval.
3. Editing on the second surface still works without losing in-flight local edits.
4. `npm test` green (new store tests covering refetch-after-`hasLoaded` and the in-flight
   guard).

## Resolution

Implemented "refetch on return" (no backend change). Files changed:

- **`src/lib/spira/store.ts`** — added `refreshGoalsIfIdle()`: a **silent** background
  refresh (does not toggle the loading banner) that re-fetches goals even after `hasLoaded`.
  It skips when `isLoading`, when a debounced write is queued (`syncTimers.size > 0`), or
  when a create is in flight (a `local-` temp id is present), and re-checks those guards
  after the fetch — so it never clobbers unsaved local edits. On `401` it redirects to
  `/login` like `loadGoals`; other errors are swallowed so a failed background refresh never
  wipes visible data. Also added `__clearPendingWritesForTests()` for test isolation.
- **`src/components/shell/AppShell.tsx`** — calls `refreshGoalsIfIdle()` on `focus`,
  `pageshow`, and `visibilitychange` (when visible), plus a light 45s poll while the tab is
  visible.
- **`src/lib/spira/store.test.ts`** — tests: refetch-after-`hasLoaded` replaces goals and
  keeps the banner off; skip while a debounced write is in flight; skip while a create is in
  flight.

Verification: `npm test` → 75 passed; `npx tsc --noEmit` clean; changed files lint clean.

**Known limitation (acceptable, self-healing):** there is a sub-second window where a
debounced write's timer has fired but its network PUT hasn't committed server-side; a poll
landing exactly there could briefly show stale data, corrected on the next refresh. Realtime
SSE (deferred) would remove this window entirely — see
`specs/2026-07-15-native-mobile-app/plan.md`, Part 1.
