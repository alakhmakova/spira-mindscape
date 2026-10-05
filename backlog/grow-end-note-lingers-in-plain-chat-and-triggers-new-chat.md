# A GROW session's end note never disappears from the plain chat, and falsely triggers "New chat"

- **ID:** BUG-077
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-03 — "когда сессия закончилась а обычном чате цели отображается
  сообщение об этом которое никогда не исчезает... и вверху появляется кнопка new chat что тоже
  странно если пользователь ничего не писал"
- **Area:** AI chat (`src/components/ai/AiPanel.tsx`,
  `android/app/.../ui/ai/AiChatViewModel.kt`)
- **Severity:** Low — cosmetic/confusing, no data loss, but visible on every GROW session

## Summary

When a GROW session ends, the panel drops back into the goal's **plain chat** and posts a short
system note there — "Session ended.", "Session memory saved.", or "Session ended without saving
memory. N proposals await your review." — as a permanent pill in the transcript. It never went
away on its own, and it was written to `msgs`/`_chatMessages`, the same list that backs the plain
chat's own "New chat" button.

That produced two visible symptoms from one cause: the note sat in the chat forever with no way to
dismiss it (short of "New chat", which discards the whole conversation), and — since the note alone
made the message list non-empty — "New chat" appeared over a plain chat the user had never actually
typed a single word into.

The owner's question ("если для основного чата grow session не даёт никакой информации — или
даёт?") was exactly right: it gives none. Both platforms already strip `system`-role messages out
of `buildHistory` before sending the plain chat's context to the model, so the note was never part
of the actual conversation the AI sees — it was purely a leftover UI artifact.

## Steps to reproduce

1. Open a goal's AI panel, start a GROW session, and let it end (naturally, via "End", or by
   review + goodbye).
2. Back in the plain chat: the "Session ended…" pill is visible and stays visible indefinitely —
   closing and reopening the panel, or even reloading, does not clear it (it is persisted).
3. Notice "New chat" is now showing in the header, even though nothing has been typed into the
   plain chat.

## Root cause

`closeSession`'s `!inGrow` branch and `leaveGrow` (web `AiPanel.tsx`; the Android equivalent is
`leaveGrow()` in `AiChatViewModel.kt`) appended a `system`-role message straight into the plain
chat's persistent message list with no expiry — unlike the panel's own toast notices
(`PanelNotice`/`notice`), which already self-clear after `PANEL_NOTICE_MS` (6s) except for the
`error` kind. The plain chat's "New chat" button is gated on `list.length > 0` /
`messages.isNotEmpty()`, so a note nobody asked for and nobody can act on was enough to make an
otherwise-untouched chat look "started".

## Fix approach

- Give the session-end note the same self-clearing behavior the panel's other transient notices
  already have, instead of writing it into the transcript permanently.

## How to verify fixed

- Repeat the reproduction steps; the note should disappear from the plain chat on its own after a
  few seconds, and "New chat" should not appear unless the user actually sent a message.

## Resolution

Fixed 2026-09-03. Both platforms: the note is still appended to the plain chat's message list (so
it's visible for a moment, in context, the way it always was) but is now removed by its own id
after `PANEL_NOTICE_MS` (web, 6000ms) / `SESSION_NOTE_MS` (Android, matching 6000ms) — the same
window the panel's own toast notices use.

- **Web** (`AiPanel.tsx`): added `postSessionNote(content)`, which appends the message then
  schedules its removal by id via `setTimeout`. All three call sites that used to push a raw
  `{ role: "system", ... }` object into `setMsgs` (`closeSession`'s `!inGrow` early return, and
  both branches of `leaveGrow`) now go through it.
- **Android** (`AiChatViewModel.kt`): `leaveGrow()` now captures the note's id, and after
  `persist()` launches a coroutine that `delay`s `SESSION_NOTE_MS` then filters the message out and
  persists again. New companion constant `SESSION_NOTE_MS = 6000L`.

Both platforms already strip `system`-role messages from `buildHistory` before sending the plain
chat's context to a provider, so this only changes what is shown on screen — the model's context
was never affected either way.

Files changed: `src/components/ai/AiPanel.tsx`,
`android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatViewModel.kt`. Verified: web
`tsc --noEmit` clean, `npm run lint` clean (0 new warnings), `npm test` 267/267 passing; Android
`:app:compileDebugKotlin` succeeds, `GrowEndingTest` passing. The user commits manually — nothing
has been committed by Claude.

### Follow-up, 2026-09-08 — the timer alone was not enough

The owner's plain chat still carried a stranded "Session ended…" note days later. The timer that
removes it lives **in the page / in the ViewModel**: the note is appended, persisted to the server
straight away, and only then scheduled for removal. Close the tab or the app inside those six
seconds — or lose the process to a background kill, which on Android is routine — and the removal
never runs, while the note is already saved. Nothing would ever take it out again.

A timer cannot make a line ephemeral; it can only make it *usually* ephemeral. So the note is now
dropped **wherever a transcript is read**, on both surfaces:

- **Web** — `parseTranscript` (`AiPanel.tsx`) filters `role === "system"` out of every transcript
  it parses, which covers the localStorage load, the server hydration and the cross-device merge.
- **Android** — `parseTranscript` (`data/ai/ChatMessage.kt`) does the same with `ChatRole.SYSTEM`.

This also **heals transcripts that are already stranded**: the note disappears on the next load and
the cleaned list is what gets persisted next. Nothing else writes a `system` message — the panel's
notices are their own surface — so there is no live message the filter can take away.

Covered by `src/components/ai/transcript.test.ts` and the `a stranded session note is dropped on
the way in` case in `TranscriptTest.kt`.

