# A long message is refused with a bare 400 that says nothing

- **ID:** BUG-083
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-08 — "это действительно был длинный текст, значит, нужно сделать 2
  вещи, позволить более длинный текст в сообщении и в случае превышения показывать не 400 а
  понятную ошибкаю, что сообщение слишком длинное"
- **Area:** `backend/.../ai/chat/dto/ChatRequest.java`, `.../web/RestExceptionHandler.java`,
  `src/components/ai/ai-api.ts`, `src/components/ai/AiPanel.tsx`, `src/lib/spira/limits.ts`
- **Severity:** Medium — the message was lost and nothing on screen or in the logs said why

## Summary

Pasting a long passage into the chat produced **"Server error: 400"** and nothing else. The message
was gone, the panel offered no explanation, and Cloud Logging held nothing but the bare status — so
from the outside it looked exactly like "no provider works, even with keys", which is how it was
first reported.

Three gaps lined up:

- the cap was **10 000 characters**, which a genuinely long note exceeds;
- the server's `ProblemDetail` **did** carry a readable reason, and the client threw it away,
  reporting only the status code;
- a rejected request was logged **not at all**, so the cause could not be found from the server side
  either.

## Steps to reproduce

1. Paste ~15 000 characters into the chat composer and send.
2. The panel shows "Server error: 400". Nothing says the message was too long.
3. Cloud Logging for that request holds no reason.

## Root cause

`@Size(max = 10_000)` on `ChatRequest.message`, a client that read only `response.status` for any
4xx, and a `MethodArgumentNotValidException` handler that returned a detail without logging one.

## Fix approach

Raise the cap, surface the server's own reason, log the rejection safely, and stop the message
reaching the wire at all when the composer can already tell.

## How to verify fixed

Paste 60 000 characters: the composer says so before sending, and Send is unavailable. A request
that does reach the server with an over-long body comes back with the readable sentence, and the
server logs `request_validation_failed fields=message`.

## Resolution

Fixed 2026-09-08.

- `ChatRequest.MAX_MESSAGE_CHARS = 50_000`, with the message the user actually reads: *"That message
  is too long — keep it under 50000 characters, or attach it as a file instead."*
- `ai-api.ts` reads the `ProblemDetail`'s `detail`/`message` for any sub-500 response and reports
  that, falling back to the status only when there is nothing to read (422 still maps to `NO_KEY`).
- `RestExceptionHandler` logs `request_validation_failed fields={}` at WARN — **field names only**.
  The messages can carry user input ("Unknown provider: …"); the field names are our own schema. See
  the never-log list in CLAUDE.md.
- The composer checks the same limit client-side (`FIELD_LIMITS.chatMessage`, mirroring the
  constant) and says so inline, so the common case never becomes a request at all.

The user commits manually.
