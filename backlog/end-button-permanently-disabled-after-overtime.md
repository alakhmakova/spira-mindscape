# "End" goes permanently dead once a GROW session's wrap-up fails

- **ID:** BUG-076
- **Status:** ✅ Fixed
- **Reported by:** Owner, running a live GROW session on Android, 2026-09-02 — "первое что я
  заметила когда сессия ушла в минус по таймеру ее уже невозможно остановить вручную"
- **Area:** AI chat, GROW session ending (`android/app/.../ui/ai/AiChatViewModel.kt`,
  `src/components/ai/AiPanel.tsx`, `src/components/ai/ai-api.ts`)
- **Severity:** High — the session becomes permanently un-endable from the UI; the only way out is
  leaving the screen

## Summary

Once a GROW session ran into overtime, the owner found the "End" pill in the header became
permanently unclickable (greyed out) — tapping it did nothing, and it stayed that way. The
timer-driven automatic wrap-up backstop was equally stuck: neither the user nor the app's own
safety net could ever end the session again.

Two related, connected root causes:

**1. `wrapUpSent` (Android) / `wrapUpRef` (web) is set `true` *before* the wrap-up network call,
and nothing reset it on failure.** `canEndEarly = !streaming && (mode == GROW_ACTIVE ||
GROW_CLOSING)` gates the End pill, but the ten-minute backstop and the manual End handler
(`closeGrow()`) both also bail out immediately whenever `wrapUpSent`/`wrapUpRef` is already `true`
— so once a wrap-up request failed (the owner's session was rate-limited by Mistral — see
`backlog/rate-limit-shown-twice-in-two-notice-shapes.md` and
`backlog/ai-panel-duplicates-system-confirmation-messages.md` for the same rate-limiting observed
independently in the same test run), Android's completion
`when` block had `failed -> Unit` for a failed `wrapUp` turn: **nothing happened**. The session
was left with `wrapUpSent = true` forever, blocking every future attempt, manual or automatic.
Web's equivalent path (`onError` → `else if (wrapUp) finishGrow();`) already did the right thing —
it ends the session on any wrap-up failure rather than leaving it stuck — so this half of the bug
was Android-only.

**2. Defensively, neither platform guaranteed `streaming`/`busy` would reset if the stream
collection itself threw** (a failure the transport layer doesn't turn into a well-formed
`ChatEvent.Error`/`onError` call) rather than reporting a normal provider error. Android's
`_streaming.value = false` sat *after* the `.collect { … }` call with no `finally`, so an uncaught
exception there would skip it, permanently locking the same `canEndEarly`/`!streaming` gate. Web's
`streamChat()` had the matching gap: `reader.read()` throwing inside the read loop propagated out
uncaught, so neither `onDone` nor `onError` ever fired and `busy` never cleared.

Also addressed per the owner's request: the composer's inline end control said **"End session
early"** while the header pill (unaffected) already said plain **"End"** — the two are now
consistently labelled "End" on both platforms, since "early"/"earlier" stops meaning anything once
the session is already in overtime.

## Steps to reproduce (before the fix)

1. Start a GROW session with a provider likely to rate-limit (a BYOK key on a low/free tier).
2. Let the session run into overtime, or tap "End" while a request is in flight.
3. Hit a provider failure on the wrap-up turn (rate limit, network blip). Android: the End pill
   stays grey/disabled from that point on, for the rest of the session.

## Root cause

See Summary. In short: an optimistically-set one-shot guard (`wrapUpSent`/`wrapUpRef`) with no
reset-on-failure path, plus (Android) a streaming-state reset that wasn't guaranteed to run on an
uncaught exception, plus (web) the same class of gap in `ai-api.ts`'s stream reader.

## Fix approach

- **Android** (`AiChatViewModel.kt`): the `send()`/`reviseProposal()` stream collection is now
  wrapped in `try { … } catch (CancellationException) { throw } catch (Exception) { failed = true;
  … } finally { _streaming.value = false }`, so streaming state always clears. The completion
  `when` block gained `failed && wrapUp -> beginEnding("")` *before* the generic `failed -> Unit`
  branch, so a failed wrap-up now always ends the session (falling back to the last real coach
  message, same as any other ending with no better record) instead of leaving `wrapUpSent` stuck.
- **Web** (`ai-api.ts`): the SSE read loop now catches a read failure and calls `onError("NETWORK")`
  before the `finally` releases the reader, guaranteeing `streamChat()` always resolves through
  exactly one of `onDone`/`onError`. Web's own wrap-up-failure handling (`finishGrow()` on error)
  was already correct and needed no change.
- **Labels**: `"End session early"` → `"End"` in both `AiChatScreen.kt` and `AiPanel.tsx`
  (composer-area control); the header pill already said "End" on both platforms.

## How to verify fixed

- `GrowEndingTest.kt` → `` `a wrap-up that fails still ends the session, not stuck with End dead
  forever` ``: scripts a provider error on the wrap-up turn and asserts the session reaches
  `GROW_END` (not stuck) with `streaming.value == false`.
- Manually: rate-limit or otherwise fail a wrap-up request (BYOK key on a low tier reproduces this
  reliably) and confirm "End" is clickable again afterward, and/or that the session actually ends.

## Resolution

Fixed 2026-09-02. Files changed: `android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatViewModel.kt`,
`android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatScreen.kt`,
`android/app/src/test/java/com/spiramindscape/android/ui/ai/GrowEndingTest.kt` (new regression
test), `src/components/ai/AiPanel.tsx`, `src/components/ai/ai-api.ts`. Verified: web typecheck
clean, `npm run lint` clean (no new warnings), `npm test` 267/267 passing; Android
`:app:compileDebugKotlin` succeeds, `GrowEndingTest` 8/8 passing (7 existing + 1 new). The user
commits manually — nothing has been committed by Claude.

Worth keeping: **an optimistic "in-flight" guard set before a network call must always have a
reset-on-failure path**, or the first failure permanently disables whatever it was guarding. The
web side had already learned this for `goodbyeSent` (`goodbye -> goodbyeSent = false` runs
regardless of `failed`, since `goodbye` is checked before `failed` in the `when`) but the same
lesson had not been applied to `wrapUp`/`wrapUpSent` on Android.