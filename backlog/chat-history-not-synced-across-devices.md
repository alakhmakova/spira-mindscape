# AI chat history is not synced across devices (phone vs. computer show different chats)

- **ID:** BUG-018
- **Status:** ✅ Fixed (2026-07-31) — implemented + tested; one manual cross-device check remains
  (see Resolution). Pending manual commit by the user.
- **Reported by:** User
- **Area:** AI assistant chat — frontend (`src/components/ai/AiPanel.tsx`, `ai-api.ts`) and backend
  (new `ai/chat/transcript/*`, `AiController`, migration `V18`)
- **Severity:** High (looks like lost data — the user believes a chat vanished)

## Summary

Start a chat with the assistant on a **phone**, then open the AI panel on a **computer** — the
computer shows a *different* (empty) chat, even though nothing was deleted. Each device had its own
conversation. Chat history did not follow the user across devices.

## Steps to reproduce

1. On device A (phone), open a goal → AI assistant → send a couple of messages.
2. On device B (computer, same account), open the same goal → AI assistant.
3. **Expected:** the conversation from device A is there.
   **Actual:** an empty/different chat; device A's messages are missing on device B.

## Root cause

The regular-chat transcript was persisted **only in the browser's `localStorage`**
(`AiPanel.tsx` → `loadTranscript` / `saveTranscript`, keyed per goal + a global bucket, capped at
100 messages). `localStorage` is **per-browser/per-device**, so two devices never shared it. The
server stored AI *proposals* and GROW *memory*, but never the chat transcript itself — chat history
was sent to the model per request (as `history`) and otherwise lived only on the client.

## Fix approach

Add a **per-user, per-scope server store** for the regular-chat transcript, and make the panel
load it on open (server = shared source of truth, **last write wins**), keeping `localStorage` as a
fast/offline cache. GROW sessions stay ephemeral (never synced).

## How it was solved (implementation)

**Backend — a new transcript store, scoped to the authenticated user:**

- **Migration `V18__ai_chat_transcript.sql`** — table `ai_chat_transcript(app_user_id, goal_id
  NULLABLE, content TEXT, updated_at)`. Two **partial unique indexes** enforce one row per
  `(user, goal)` and exactly one **global** row per user (a plain `UNIQUE` would treat NULL
  `goal_id`s as distinct and allow many global rows). `goal_id` has `ON DELETE CASCADE`.
- **`AiChatTranscript`** entity + **`AiChatTranscriptRepository`**
  (`findByAppUserIdAndGoalId` / `…GoalIdIsNull`).
- **`AiChatTranscriptService`** — `get` / `save` (upsert, last-write-wins) / `clear`, every call
  scoped to `CurrentUserProvider.getCurrentUser().getId()` so a user can only ever touch their own
  rows. Blank content normalises to `"[]"`.
- **`AiController`** — `GET /api/ai/chat/transcript?goalId=`, `PUT /api/ai/chat/transcript`
  (`SaveTranscriptRequest`, `content` `@Size(max=400_000)`), `DELETE /api/ai/chat/transcript`.
  These sit under `/api/**` → already **auth-required + CSRF-protected** by `SecurityConfig`.

**Frontend — load-on-open + push-on-turn, without clobbering:**

- `ai-api.ts` — `getTranscript` / `putTranscript` / `deleteTranscript` (all best-effort: a failure
  never blocks the chat, so it still works offline from the local cache).
- `AiPanel.tsx`:
  - **Hydration effect** (mount + scope change): pull the server copy; if it has messages, adopt it
    and refresh the local cache; if the server is empty but this device has local history, **seed**
    the server with it. A `hydratingRef` (starts `true`) makes the persist effect **skip pushing**
    while a pull is in flight, so a stale local copy can't overwrite a newer conversation from
    another device. A resolved fetch re-checks the scope and `busy` before applying.
  - **Persist effect** (after each settled turn): write local **and** `PUT` to the server (skipped
    while hydrating, and for the deliberate "New chat" clear).
  - **"New chat"** now also `DELETE`s the server row (a `skipServerPutRef` stops the empty state
    from re-creating it), so clearing on one device clears everywhere.
  - Attachment **file bytes are stripped** (`dataUrl → ""`) before storing (`messagesForStore`), so
    the synced blob and `localStorage` stay small — only the chip labels are kept.

**Conflict model:** last-write-wins per turn. Whichever device most recently completed a turn
defines the shared history; other devices pick it up the next time the panel opens. (Not real-time
live sync — that would need websockets/polling — but it resolves "open on the computer and see the
phone's chat".)

## How to verify fixed

1. Automated: `AiChatTranscriptServiceTest` (7 tests) — user-scoping, upsert last-write-wins,
   global-vs-goal scope, blank→`"[]"`, and the **cross-user isolation** boundary (a user only ever
   reads/writes rows keyed by their own id; `clear` never deletes another user's row). All backend
   AI tests (139) and frontend tests pass; `tsc`/`eslint` clean.
2. Manual (owner): send a message on device A, open the assistant on device B (same account, same
   goal) → device A's messages appear. "New chat" on one device clears it on the other after
   reopening.

## Follow-up: provider/model selection also wasn't synced (2026-07-31)

The user noted the **selected provider and model** also differed per device. Cause: the per-provider
**model** is already server-side on `ai_api_keys` (synced), but the **active provider selection**
lived only in `localStorage` (`ACTIVE_PROVIDER_KEY`) — so each device could show a different active
provider (and hence its model).

Fixed by persisting the active provider per user:
- **Migration `V19__user_preferred_ai_provider.sql`** — `app_user.preferred_ai_provider VARCHAR(32)`.
- **`AppUser.preferredAiProvider`** field; **`AiPreferenceService`** (get/set, scoped to the current
  user, read from the DB so it's never a stale session value).
- **`AiController`** — `GET`/`PUT /api/ai/preferences` (`SavePreferencesRequest.provider` validated
  against the provider `@Pattern`), under the auth+CSRF-protected `/api/**`.
- **Frontend** (`ai-api.ts` `getAiProvider`/`saveAiProvider`; `AiPanel.tsx`): on load, choose the
  active provider as **server → local → first available key** (server wins when it still has a key);
  every explicit provider choice (activating one, or saving a new key) `PUT`s it to the server.
- Test: `AiPreferenceServiceTest` (get/get-null/set persists on the user's own row). Verified live:
  `GET` null → `PUT GEMINI` → `GET` returns `GEMINI`.

## Follow-up: real-time sync (updated only on reload, not live) (2026-07-31)

The user noted the chat updated only after a manual **reload** — it should update **in real time**
while the panel is open. The initial fix loaded the server copy only on panel open (last-write-wins
per open), so a message sent on one device didn't appear on another until the second device
reopened/reloaded.

Fixed with **short-interval polling while the panel is open** (no websocket infra needed — the
transcript changes per *turn*, so a few-second poll is effectively live):

- **`AiPanel.tsx`** — a poll effect (every **4 s**) that runs only while the panel is open
  (`PanelContent` mounts only when open) and the tab is **visible**. On each tick it `GET`s the
  scope's transcript and, if the server's `updatedAt` is **newer than the last one we saw/wrote**,
  adopts the server messages (`setMsgs` + refresh local cache). Guards: never adopt mid-stream
  (`busy`) or during hydration; skip background tabs (`document.visibilityState`); and set
  `skipServerPutRef` so an adopted copy is **not echoed back** — preventing a PUT/adopt **ping-pong**
  between two open devices.
- **`putTranscript`** now returns the server's new `updatedAt`; hydration and every write record it
  in `lastSeenUpdatedRef`, so the poll only reacts to changes from **another** device, never its own
  writes.

Chosen polling over SSE/websocket push deliberately: the app has no websocket infrastructure, and a
turn-based transcript doesn't need per-keystroke fidelity — a 4 s poll gives live-feeling updates
with far less complexity and fewer failure modes (reconnection, per-user emitter registry). Verified
live with two browser sessions: a message written to the server by one session appears in the
other's open panel in **~3 s with no reload** (Playwright: external `PUT` → poll adopts the new
message).

## Resolution

Implemented as above. Files: `V18__ai_chat_transcript.sql`; `ai/chat/transcript/`
(`AiChatTranscript`, `AiChatTranscriptRepository`, `AiChatTranscriptService`, `dto/TranscriptDto`,
`dto/SaveTranscriptRequest`); `AiController` (3 endpoints); `ai-api.ts` (3 client functions);
`AiPanel.tsx` (hydration + persist + "New chat" sync, attachment-byte stripping). Tests:
`AiChatTranscriptServiceTest`. The user commits manually. Remaining: the one manual two-device
check above.
