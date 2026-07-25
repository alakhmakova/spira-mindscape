# Goals list downloads every file resource's contents on every load

- **ID:** BUG-012
- **Status:** ✅ Fixed (2026-07-25) — see Resolution. Pending manual commit by the user.
- **Reported by:** Claude (found while investigating BUG-011), confirmed by the user
- **Area:** Frontend web app — data loading (`src/lib/spira/api.ts`, `src/lib/spira/store.ts`,
  `src/components/spira/Resources.tsx`)
- **Severity:** High (slow loads; on mobile the whole goals list can fail to load)

## Summary

The goals query (`GOAL_FIELDS`) requested **`dataUrl` for every resource of every goal**. A
file's `dataUrl` is the whole file as a base64 data URL, so opening the app downloaded **all
attached PDFs and images at once** — even though nothing was displaying them yet. With a handful
of real (multi-MB) PDFs this makes the initial payload tens of megabytes: slow everywhere, and
on a phone the request can time out or reset, which fails **the entire goals list**, not just
one file.

## Steps to reproduce

1. Attach two or three real, multi-MB PDFs to goals.
2. Load the app (especially on a phone / throttled connection) and watch the `POST /graphql`
   response for the `goals` query in devtools.

**Expected:** the list payload is small — metadata only; file bytes load when a file is opened.
**Actual:** the response contains every file's full base64 contents; load is slow and can fail
outright on mobile (taking the whole dashboard with it).

## Root cause (confirmed)

`GOAL_FIELDS` in `src/lib/spira/api.ts` selected `dataUrl` inside `resources { … }`. Every
`fetchGoals()` — which also runs on the 45s background refresh and on tab focus (BUG-001) —
therefore re-downloaded every file body, repeatedly, for data that is only needed when the user
actually opens a specific file.

## Fix approach

Load file contents **lazily, per resource**, and keep the list metadata-only. The backend
already exposes `resourceById(id: ID!)`, so no backend or schema change is needed.

## How to verify fixed

1. Load the app with several file resources attached and inspect the `goals` GraphQL response —
   it must contain **no** `dataUrl`, and stay small (~tens of KB even for 100 goals).
2. Open a file resource → a separate small `ResourceFile` query fires and the PDF/image renders
   (a spinner shows while it loads).
3. Open the same file again → **no** second request (it is cached on the resource).
4. Rename a file resource **without** opening it → the stored file is not blanked
   (its contents still open correctly afterwards).
5. `npm run test:e2e` → `pdf.spec.ts` and `image.spec.ts` pass (both now exercise the lazy path).

## Resolution

Implemented lazy per-file loading (no backend change — reuses the existing `resourceById`
query). Files changed:

- **`src/lib/spira/api.ts`** — removed `dataUrl` from `GOAL_FIELDS.resources` (with a comment
  explaining why); added `fetchResourceFile(id)` which queries `resourceById(id) { id dataUrl }`
  and returns just the data URL.
- **`src/lib/spira/store.ts`** — added `loadResourceFile(goalId, resourceId)`: returns the
  cached `dataUrl` if already present, otherwise fetches it, **caches it onto the resource** in
  the store, and returns it. Concurrent callers for the same resource share one in-flight
  request via a module-level `fileLoads` map. Errors surface through `setSyncError` and resolve
  to `""` rather than throwing.
- **`src/components/spira/Resources.tsx`** — the preview triggers `loadResourceFile` on open
  (`useEffect`) and shows a `Loader2` spinner until the bytes arrive; card and preview
  **copy**/**download** actions await the file first (`ensureFile()` / `loadFile()`); and the
  edit form omits `dataUrl` from its payload when the user did not pick a new file, so editing
  a not-yet-loaded file cannot blank it.

Measured after the change: the goals-list response for **100+ goals is ~51 KB** (previously it
carried every attached file in full).

Verification: `npx tsc --noEmit` clean; changed files lint clean; `npm test` → 75 passed;
`npm run test:e2e` → `pdf.spec.ts` + `image.spec.ts` passed.
