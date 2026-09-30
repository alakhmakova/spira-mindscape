# An open resource panel is torn down and scrolled back to the top every 45 seconds

- **ID:** BUG-090
- **Status:** ✅ Fixed
- **Reported by:** the owner, 2026-09-22 and again 2026-09-23 — "локальное приложение периодически
  мигает, как будто перезагружается", then "что со скачками страницы… я просила разобраться, но
  скачки продолжаются"
- **Area:** Web — `src/lib/spira/store.ts` (`refreshGoalsIfIdle`, `loadGoals`, `refreshGoals`), and
  every panel that reads a lazily-loaded field: the vacancy map, the image and PDF viewers
- **Severity:** **High** — it happened on a timer, on the production build, to anyone reading or
  filling in a vacancy map. Losing your place every 45 seconds makes a long page unusable, and the
  same tick re-downloaded a file's bytes over the wire.

## Summary

Every 45 seconds the shell polls for cross-device changes (`refreshGoalsIfIdle`, `AppShell.tsx`).
When the goal graph's revision has moved, it fetches the goals list and replaces the store's
`goals` wholesale.

**A list read deliberately does not select the heavy fields.** `ResourceView` returns null for a
file's `dataUrl` and for a vacancy map's `mapData`, so a goals list carries neither; they are
fetched on demand by the panel that opens them (`loadResourceFile` / `loadResourceMap`). The
wholesale replace therefore handed the store resources whose heavy fields had *vanished* — and the
open panel is precisely the thing that reads them.

So the panel saw `mapData === undefined`, which it correctly reads as "never fetched", fell back to
"Loading the map…", re-fetched the document, and re-rendered its body from scratch. The scroller
was a new element, so **the reader was thrown back to the top of the page**.

The first investigation (2026-09-22) found the Vite dev server restarting on an `.env.local` change
and HMR updates, which is a real and separate cause of flicker *while an agent is editing files* —
and stopped there, because an **idle** dashboard showed nothing in 95 seconds. That was the mistake:
the defect needs an open resource panel, and it is invisible on the page the probe was watching.

## Steps to reproduce

1. Run the stack (or open the tunnel), open a goal → Resources → a vacancy map.
2. Scroll down inside the panel — say to Company information.
3. Wait up to 45 seconds without touching anything.
4. Observed: the panel blinks through "Loading the map…" and the scroll position resets to the top.
   A third GraphQL request goes out on the same tick, re-fetching the document just discarded.

Measured on the built bundle with a `PerformanceObserver` on `layout-shift` plus a capture-phase
`scroll` listener:

```
45.6s  POST /graphql        <- revision check
45.7s  POST /graphql        <- the goals list
45.8s  POST /graphql        <- re-fetching the map it had just dropped
45.8s  [scroll] div.min-h-0.flex-1.overflow-y-auto 1022→0
```

## Root cause

`set({ goals })` in `refreshGoalsIfIdle` (and in `loadGoals` / `refreshGoals`) treats the list read
as the whole truth. For `dataUrl` and `mapData` it is not: a null there means "not selected", never
"empty". The client's own comment on `loadResourceMap` says as much — `undefined` is "never
fetched" — and the refresh was manufacturing exactly that state under an open panel.

## Fix approach

`keepLazyFields(previous, next)` in `store.ts`: when a freshly fetched list replaces the goals,
carry over the lazily-loaded fields we already hold for resources that still exist. Applied to the
background poll and to both full reloads. Everything else in the refresh is unchanged — a rename
made on another device still lands.

## How to verify fixed

- `src/lib/spira/store.test.ts` → "keeps a loaded map and file across a refresh, so an open panel is
  not torn down": the fetched list omits `mapData` and `dataUrl`, and after the refresh the cached
  values are still there while the goal's new title has been applied.
- By hand: repeat the steps above and sit through two polls. The scroll position holds, "Loading the
  map…" never appears, and the tick makes two requests, not three.

## Resolution

Fixed 2026-09-23 in `src/lib/spira/store.ts` (`keepLazyFields`, used by `refreshGoalsIfIdle`,
`loadGoals` and `refreshGoals`), covered by the store test above. Re-measured on the built bundle:
no scroll jump on the 45-second tick, and the tick is back to two requests.

**One deliberate trade-off.** An open panel no longer picks up a map that changed *on the server*
(another device, or the CV writer) — it will show that on the next open. Before this fix it did pick
it up, but only as a side effect of the defect and at the cost of the jump. If live updates of an
open map are wanted, they need their own quiet re-read that swaps the document in place rather than
remounting the panel.
