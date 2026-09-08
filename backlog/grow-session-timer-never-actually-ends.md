# A GROW session's timer never enforces its own limit

- **ID:** BUG-074
- **Status:** ✅ Fixed
- **Reported by:** Claude, running a live 15-minute GROW session at the owner's explicit request
  to stay in it until the timer actually reached zero, 2026-09-02
- **Area:** Frontend AI chat, GROW session timer (`src/components/ai/AiPanel.tsx`)
- **Severity:** Low/Medium — nothing breaks, but the "15 min" the user picks when starting a
  session is not a real limit, and the UI never says so

## Summary

Starting a GROW session offers a duration (15/30/45/60 min) framed as *"Focused time on a single
goal… We have fifteen minutes."* In a real test, holding a reply unsent and letting the in-app
timer count down for real (no synthetic time-skipping) showed:

- at **01:18 remaining**, a banner appears in the header: *"⏳ The session is gently moving toward
  a close"*;
- the timer then **crosses zero and keeps counting up** (`+00:31`, `+00:50`, `+01:17` observed) —
  the composer stays fully live, and a message sent at `+00:31` was answered normally by the coach;
- nothing about the session changes at 0:00 itself — no forced wrap-up, no blocked input, no
  visible acknowledgement that the chosen time has elapsed beyond the one static banner line.

This may be entirely intentional (a soft nudge rather than a hard cutoff, so the user is never cut
off mid-thought), but as implemented the "15 minutes" promised at session start is not a real
constraint, and the only signal the user gets is a banner that stops updating once shown (it does
not, for example, count how far over time the session now is).

## Steps to reproduce

1. Start a GROW session, pick "15 min".
2. Let the conversation run for real wall-clock time (not compressed) past the 15-minute mark,
   without triggering an early "we're done" — keep answering normally.
3. Watch the header timer: banner at ~01:18 remaining, then counts past 0:00 into positive overtime
   with no further change in behavior.

## Root cause

Not a defect in the strict sense — the timer display and the "gently moving toward a close" banner
are implemented, but there is no code path that actually ends the session, blocks the composer, or
otherwise treats 0:00 as a real deadline once the banner has been shown.

## Fix approach (needs a product decision first)

- If a soft nudge is the intended design: no code change needed, but consider making the overtime
  visible in the header (e.g. showing accumulated overtime is already done — `+MM:SS` — worth
  confirming this is deliberate rather than an oversight) and/or a second, stronger nudge if
  overtime passes some further threshold.
- If a hard 15-minute limit is intended: the session should move to wrap-up on its own once the
  timer reaches 0:00, the same way it already does when the user gives a natural closing line (that
  part works correctly — see the related session in this same test run).

## How to verify fixed

- Depends on the product decision above; verify whichever behavior is chosen actually happens at
  0:00, not just at session start.

## Resolution

Owner's decision (2026-09-02): soft nudge is correct — a session the user is actively continuing
must not be cut off just because the planned length passed — but it needed a real backstop for a
session that has actually been abandoned, and the backstop is measured by **inactivity**, not by
total overtime elapsed. Rule: once the session is in overtime, 10 minutes with no message from the
user ends it (wrap-up is sent automatically); any real message resets that clock, so picking the
conversation back up buys another 10 minutes, repeatedly, for as long as the user keeps it going.

Implemented on both surfaces (`overtimeInactivitySeconds` / `overtimeInactivityRef`, reset in
`send()` / `sendGrow()` on every real user turn, checked each timer tick only while `remaining <
0`) — see `android/app/.../ui/ai/AiChatViewModel.kt` (`startTimer`, `OVERTIME_INACTIVITY_SECONDS`)
and `src/components/ai/AiPanel.tsx` (the GROW timer `useEffect`s, `OVERTIME_INACTIVITY_SECONDS`).
This replaced the previous one-shot, elapsed-overtime-only backstop
(`OVERRUN_GRACE_SECONDS`/`OVERRUN_GRACE`), which is what let a session with an active user still
sit through an auto-wrap-up — and, combined with [[end-button-permanently-disabled-after-overtime]],
was part of why "End" appeared to do nothing once the old backstop had already fired and failed
(`backlog/end-button-permanently-disabled-after-overtime.md`).

Files changed: `android/app/src/main/java/com/spiramindscape/android/ui/ai/AiChatViewModel.kt`,
`src/components/ai/AiPanel.tsx`. Android: `GrowEndingTest.kt` covers the related failed-wrap-up
fix; the inactivity reset/re-arm itself is timing-based and was verified by code review + the
existing test suite (267/267 web, 8/8 `GrowEndingTest`), not a dedicated timer test.
