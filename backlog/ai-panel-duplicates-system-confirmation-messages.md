# The AI panel posts some of its own status lines twice

- **ID:** BUG-069
- **Status:** ✅ Fixed
- **Reported by:** Claude, running two independent live GROW sessions at the owner's request
  (BYOK Mistral, `mistral-large-latest`), 2026-09-02
- **Area:** Frontend AI chat (`src/components/ai/AiPanel.tsx`)
- **Severity:** Medium — confusing but not destructive; happened consistently around provider
  rate-limit failures

## Summary

At least two of the AI panel's own system/status lines were seen posted **twice in a row as
separate messages**, in two separate sessions:

- **Session 1:** after asking "Are you there?" following a failed turn, the assistant's line
  **"I've prepared this for your review."** appeared twice, back to back, immediately before the
  session wrap-up card.
- **Session 2 (independent session, same goal, later):** the **same** "I've prepared this for your
  review." duplication happened again, and separately, after clicking "Save memory", the
  confirmation **"Session memory saved."** also appeared twice in a row.

Both sessions were hitting the Mistral rate limit around the same moments (see BUG-068), which is
the likely trigger — the messages look like a client-side retry re-posting the same canned line
without checking whether it was already shown.

## Steps to reproduce

1. Connect a rate-limited BYOK provider (Mistral on a free/low tier reproduced this reliably).
2. Run a GROW session long enough to hit the rate limit a few times.
3. Say a closing line (e.g. "I think that's everything for today") or explicitly ask to finish, so
   the panel posts "I've prepared this for your review." and later "Session memory saved." — watch
   for either line appearing twice consecutively.

## Root cause

Not isolated — needs someone to trace the retry/resend path around these two canned strings in
`AiPanel.tsx`. Given both duplicates showed up right around rate-limit errors, the likely
mechanism is a retry that resends the whole request (including the client-side "I've prepared…" /
"Session memory saved." acknowledgement) without deduping against what is already in the
transcript/notice state.

## Fix approach (proposed)

- Key these canned confirmation posts by an idempotency token (e.g. the request/turn id) so a
  retry cannot re-emit the same line twice.

## How to verify fixed

- Reproduce steps above under an artificially rate-limited provider (or a mocked 429) and confirm
  each canned confirmation line appears at most once per event.

## Resolution

Fixed 2026-09-02. Two distinct, unrelated causes, both fixed:

1. **"I've prepared this for your review." shown twice.** Not a re-entrancy bug — two genuinely
   different messages happened to use the identical literal fallback text. The GROW session's
   *ending* turn (the one calling `end_session`) fell back to this exact sentence whenever the
   model wrote no prose alongside its tool calls (a normal, common reply shape) — even though its
   proposals are *held back*, not shown, until the record is decided. Later, once the user actually
   clicked Save/Discard, `closeSession` posted a *second*, separate message with the same text for
   the real review card. Fixed in `AiPanel.tsx`'s `sendGrow` `onDone`: a wordless ending turn now
   drops its placeholder bubble entirely (`blankEndingTurn`) instead of filling it with text that
   was both premature and a duplicate — the `SESSION WRAP-UP` card that follows is what represents
   that moment.
2. **Defensive hardening**: neither `closeSession` nor `leaveGrow` (web) / their Android
   equivalents guarded against being invoked twice — every other one-shot step of the ending
   sequence already does (`wrapUpSent`/`wrapUpRef`, `goodbyeSent`/`goodbyeRef`). Added matching
   guards (`closeSessionRef`/`leaveGrowRef` on web, `closeSessionCalled`/`leaveGrowCalled` on
   Android), reset when a new session starts. This didn't turn out to be the cause of what was
   originally observed, but it closes a real gap the rest of the file's own pattern implies should
   not exist, and Android's `GrowEndingTest` regression test (added for this) does catch a genuine
   duplicate with the guard removed.

Verified live: reproduced the exact original session shape (commit, then "let's end the session")
before and after the fix. Before: "I've prepared this for your review." appeared twice, back to
back, ahead of the wrap-up card. After: it appears exactly once, and only after Save/Discard is
chosen.

Files changed: `src/components/ai/AiPanel.tsx`,
`android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatViewModel.kt`,
`android/app/src/test/java/com/spiramindscape/android/ui/ai/GrowEndingTest.kt` (new regression
test: `` `closeSession and leaveGrow ignore a second call - no duplicate messages` ``). Verified:
web `tsc --noEmit`/`eslint`/`npm test` (267/267) clean; Android `compileDebugKotlin` succeeds,
`GrowEndingTest` 9/9 passing (confirmed the new test fails red with the guards removed).
