# BUG-037 — Android note edits can be lost silently

- **Status:** 🔧 In progress
- **Area:** Android note editor (`ui/goals/NoteEditorActivity.kt`), resource mutations
- **Severity:** High — a user's written note can fail to persist with no sign at all
- **Reported by:** Owner (GRO-133, 2026-08-17): "Записи в note не были сохранены… этой проблемы нет на вебе, в чём разница?"

## Summary

On Android a note autosaves its HTML continuously through `updateResource(body=…)`. The save was
wrapped in a bare `runCatching {}` with **no logging and no user feedback**, so a save that failed
(a network blip, an auth/session lapse, a server error) was both **invisible and lost** — the
editor looked identical whether the note persisted or not. The web does not show this because its
edits go through the optimistic store, which funnels failures into a visible sync-error banner.

## Steps to reproduce

1. Open a note on Android and type.
2. Have a save fail (e.g. drop connectivity, or the session expires) while autosave fires.
3. Close and reopen the note — the latest edits are gone, and nothing ever indicated a problem.

## Root cause

Three contributing factors:

1. **Silent failure (fixed here).** `saveBody` / `saveTitle` swallowed every exception; a failed
   mutation produced no log and no UI, so lost edits could not be noticed or diagnosed — the
   BUG-034 "invisible AND lost" class the logging rules call out.
2. **A teardown race on the way out (found and fixed 2026-08-17).** `finish()` read the final HTML
   with `controller.withHtml { … }` — which goes through `evaluateJavascript`, and is
   **asynchronous** — and then called `onDone()` immediately. Leaving therefore navigated away and
   destroyed the WebView while the read was still in flight, so the callback carrying the last
   keystrokes often never arrived. This makes the *most recent* edits the ones most likely to
   vanish, which matches the report exactly. The web has no equivalent because its editor writes
   through the optimistic store rather than reading itself on the way out.
3. **Whether anything else remains is not yet pinned down** and needs on-device reproduction
   (`adb logcat`, a real account against the production backend the app points at). Remaining
   candidates: a transient network failure with no retry/queue (unlike the web's optimistic store),
   or a session/auth lapse.

## Fix approach

- **Done:** make the save non-silent — `SpiraLog.w("note_body_save_failed" / "note_title_save_failed", e)`
  and a `SpiraInlineBanner` so the user is told their latest edits may not be stored. This both
  stops the silent loss and gives us the log needed to identify the real cause on the next report.
- **Done (2026-08-17):** close the teardown race — `finish()` now waits for the HTML to come back
  before calling `onDone()`, with a 400ms timeout so a wedged page cannot strand the user on the
  screen, and a `once` guard so whichever of the two fires first wins.
- **Next (needs device):** reproduce with logcat to classify any *remaining* failure, then add the
  right durability — a retry/queue for transient failures. Consider routing note saves through the
  same resilient path the rest of the app uses rather than a one-off mutation.

## How to verify fixed

- With connectivity dropped, editing a note now shows the save-failure banner (no more silent loss),
  and `note_body_save_failed` appears in logcat / Crashlytics.
- Once the durability work lands: edit a note offline, restore connectivity, reopen — the edits are
  present.

## Resolution

_Partial (2026-08-17): two of the three causes are fixed — the **silent failure** (saves now log and
raise a banner) and the **teardown race** on leaving the editor, which is the one that best explains
"my last edits are gone". Whether a transient network or session failure also contributes is still
open and needs on-device reproduction; the logging added here is what will identify it._
