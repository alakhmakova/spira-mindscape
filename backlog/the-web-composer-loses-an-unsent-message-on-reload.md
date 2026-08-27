# The web composer loses an unsent message on reload — draft and attachments both

- **ID:** BUG-049
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-24, after the Android half landed — "на вебе вложения композера не
  переживают перезагрузку страницы … сделай это"
- **Area:** Web (`src/components/ai/AiPanel.tsx` — `Composer`, and the new
  `src/components/ai/composer-draft.ts`)
- **Severity:** Medium — silent loss of work the user has done, in the flow that is most expensive
  to redo (a file has to be found and picked again)

## Summary

Type a message to the coach, attach one of the goal's resources and a photo, then reload the page —
by accident, because the tab crashed, or because the browser restored the session. Everything is
gone: the sentence, both chips, with nothing said.

Android has survived the equivalent (the process being killed) since BUG-050, so the two surfaces
disagreed on whether a half-composed message is worth keeping.

## Steps to reproduce

1. Open a goal in the web app, open the AI coach.
2. Type something into the composer; attach a resource and a file.
3. Press F5.
4. The composer is empty.

## Root cause

The draft and the attachment list lived in `Composer`'s own `useState`, with `initialValue` only
carrying the text across a **remount** inside the same page (the GROW end card replacing the
composer). Nothing was ever written down, so a page load started from nothing.

## Fix approach

A small store, `src/components/ai/composer-draft.ts`, shaped deliberately like Android's:

| | Android | Web |
|---|---|---|
| draft + chip metadata | `SavedStateHandle` | `localStorage` |
| a chip's bytes | a file parked in the app cache, referenced by path | a record in **IndexedDB**, referenced by key |

**The bytes do not go in `localStorage`.** Six attachments can be several megabytes and the quota
is about five for the whole origin — shared with the chat transcript, which is the more important
thing in there, so writing them would risk a `QuotaExceededError` that takes the transcript down
too. IndexedDB has its own far larger budget and is the browser's nearest equivalent to the cache
directory Android parks its copies in.

The composer reads the draft back on mount and writes it on a 400 ms debounce; a chip that is
already stored keeps its key, so typing a letter does not rewrite megabytes. Sending clears it, or
the restored draft would come back as a duplicate of something already in the transcript. A chip
whose bytes have gone is dropped rather than restored as a name that would send nothing. Every
storage call is best-effort: private browsing or a blocked database costs the user nothing more
than the behaviour they had before.

**A GROW session deliberately keeps nothing** (`draftScope` is undefined there): the session is
ephemeral by design, and half a sentence typed into one has no meaning once it is over.

## How to verify fixed

- `src/components/ai/composer-draft.test.ts` — nine assertions on the store, including that the
  saved `localStorage` row contains neither `base64` nor the bytes, that an orphaned blob is
  collected when its chip is removed, and that each chat's draft stays its own.
- `e2e/ai-composer-draft.spec.ts` — the real thing: type, attach a resource and a photo,
  `page.reload()`, and find all three still there; and a second test that sending clears it.
  Confirmed **failing** before the fix ("Expected: …salary band?  Received: ''").

## One thing this nearly shipped with

The first version found orphaned blobs by reading every record back (`store.getAll()`) on each
debounced save — so with a photo attached, the whole image came out of the database again every few
keystrokes. It did not fail anything; it just made the composer slow enough that
`ai-attach-both.spec.ts` started timing out waiting for a popup, at **2.5 minutes** per test.

That looked exactly like flake and was not: a different test failed each run, but always in the same
spec, and always on a timeout rather than an assertion. Answering the same question through the
`scope` **index** (`getAllKeys`, no values) took the same tests to **22 seconds**. The rule to take
from it is the one already in CLAUDE.md, read the other way round: a spec that got *slower* the day
the product changed is the product's fault, not the runner's.

## Resolution

Fixed 2026-08-24, together with the second half of BUG-050 — the same question asked on the other
surface.
