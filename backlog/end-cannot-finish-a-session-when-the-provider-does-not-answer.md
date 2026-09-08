# "End" cannot finish a session when the provider does not answer

- **ID:** BUG-079
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-08 — "я даже не могу завершить сессию из-за этого, я нажала end,
  потом стоп, и после этого нет никакой возможности завершить сессию. сессия по end должна
  завершаться даже есть провайдер не отвечает, не нужно пытаться сохранить в память что-то, если
  end то это завершение без участия ai должно быть"
- **Area:** AI chat (`src/components/ai/AiPanel.tsx`,
  `android/app/.../ui/ai/AiChatViewModel.kt`, `.../ui/ai/AiChatScreen.kt`)
- **Severity:** High — the user was trapped in a session with no way out

## Summary

**End was not an exit — it was a request to the model.** Pressing it sent a hidden instruction
("wrap this up now") and waited for the coach to answer with a closing turn and a record. When the
provider was unreachable — Mistral was, for the whole of that afternoon — nothing came back and the
session simply stayed open. Pressing Stop then cancelled the stream and left `wrapUpSent` latched
true, so **End was permanently disabled** and the session could not be finished at all.

The owner's rule is the correct design and is now the contract: **End ends the session, locally,
with no AI involved and nothing saved.** A record is something the coach offers; it is not
something End waits for.

## Steps to reproduce

1. Point the panel at a provider that is failing (any BYOK key past its quota will do).
2. Start a GROW session, exchange a turn, then press **End** and confirm.
3. The panel waits for a closing turn that never arrives. Press **Stop**.
4. **End is now greyed out for good.** Nothing in the panel finishes the session.

## Root cause

Three separate things, one behaviour:

- `EARLY_END_INSTRUCTION` — End's real implementation was a turn sent to the model, so a dead
  provider meant a session that could not close.
- `canEndEarly` was gated on the session being idle **and** no wrap-up having been sent, so the
  latched `wrapUpSent` disabled the button permanently.
- `cancelStream()` (Android) reset neither `wrapUpSent`, `goodbyeSent` nor the overtime counter, so
  Stop left the state machine mid-close with no way back.

## Fix approach

Make End local and unconditional, and make Stop leave a state the session can be ended from.

## How to verify fixed

Repeat the reproduction with a provider that is definitely failing. End must close the session
immediately, every time, with no network call — and the closing card must say plainly that no
record was written rather than offering to save an empty one (BUG-084).

## Resolution

Fixed 2026-09-08, both surfaces.

- `EARLY_END_INSTRUCTION` is **deleted**. End now runs entirely in the client: cancel any stream in
  flight, stop the timer, clear the draft and any held proposals, and leave the session
  (Android's `endGrowNow()`; the web's End path in `AiPanel.tsx`).
- `canEndEarly` is now simply "we are in a session" (`const canEndEarly = inGrow;` /
  `canEndEarly = isGrowSession(mode)`) — nothing about the provider can take the exit away.
- Android's `cancelStream()` resets `wrapUpSent`, `goodbyeSent` and `overtimeInactivitySeconds`.
- The confirm dialog was reworded to match what actually happens: "Yes, end it" / "No, keep going",
  with no promise that anything will be saved.

Covered by `GrowEndingTest` (Android) and the web unit suite. The user commits manually.
