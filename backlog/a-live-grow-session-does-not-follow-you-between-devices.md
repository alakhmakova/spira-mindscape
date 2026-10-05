# A live GROW session does not follow you between devices

- **ID:** BUG-081
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-05 — "сначала я начала сессию на веб мобайл, потом перешла на
  андроид и мне пришлось начать сессию заново, нет синхронизации ai сессиий - это точно нужно
  исправить"
- **Area:** AI chat (`src/components/ai/AiPanel.tsx`, `ai-api.ts`;
  `android/app/.../ui/ai/AiChatViewModel.kt`, `data/ai/AiApi.kt`); backend `ai/grow/session/`
- **Severity:** Medium — no data was lost, but a session in progress could not be picked up
  anywhere else

## Summary

The ordinary chat has synced across devices for a while (`ai_chat_transcript`). A **session in
progress** did not: it lived in the browser's `localStorage`, and on Android only in the ViewModel.
Starting one on the phone and reaching for the laptop meant starting again from nothing — the
minutes, the focus and everything said so far stayed on the device it began on.

## Steps to reproduce

1. Start a GROW session on mobile web for some goal; exchange a few turns.
2. Open the same goal in the Android app (or on the laptop).
3. There is no session. Starting one begins from zero.

## Root cause

Never implemented — the session state was deliberately client-local when GROW sessions were built,
and stayed that way when the transcript itself moved to the server.

## Fix approach

Give the live session a row of its own, keyed per user and goal, written whenever the session state
changes and deleted the moment it ends.

## How to verify fixed

Start a session on one surface, then open the goal on the other: it offers to resume, with the
minutes, the focus and the conversation intact. End it anywhere — it is gone everywhere.

## Resolution

Fixed 2026-09-08. New backend module `ai/grow/session/` — `GrowSession`, `GrowSessionRepository`,
`GrowSessionService` — behind `GET` / `PUT` / `DELETE /api/ai/grow/session`, with migration
`V22__ai_grow_session.sql`. One row per (user, goal), unique-indexed: a second would be two clocks
running on one conversation.

- Owner-scoped through `goalRepository.findByIdAndUserId`, like every other goal-owned resource — a
  goal id from the request is never trusted.
- `content` is the client's own JSON. The server does not read it, so the schema does not have to
  move when the session's shape does; both surfaces write the same shape.
- Deliberately **not** the transcript's row: a session row is created when the session starts and
  deleted the moment it ends. What outlives it is the record the user chooses to keep and whatever
  they approved into the goal.
- Web: `saveGrowSession` also `PUT`s, `clearGrowSession` also `DELETE`s, and `resumeFromServer`
  (guarded by `resumeAskedRef`) offers the session found on the server. Android:
  `persistGrowSession` / `restoreGrowSession` / `clearGrowSessionRemote`.

The user commits manually.
