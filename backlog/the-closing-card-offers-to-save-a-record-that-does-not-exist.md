# The closing card offers to save a record that does not exist

- **ID:** BUG-084
- **Status:** ✅ Fixed
- **Reported by:** Claude, auditing the End work at the owner's request, 2026-09-08
- **Area:** `src/components/ai/AiPanel.tsx` (`GrowEndCard`),
  `android/app/.../ui/ai/AiChatScreen.kt` (`GrowEndCard`)
- **Severity:** Medium — silently overwrote a previous session's memory with nothing

## Summary

A session can end with no record at all: the provider fails mid-close, or **End** is pressed and the
session ends locally with no AI involved (BUG-079). The closing card handled that badly on both
surfaces.

- **Web:** with no record it showed no preview, **hid the revise field** — the one useful thing left
  to do — and still offered a live **"Save memory"** button. Pressing it wrote a blank memory **over
  whatever the previous session had left**, which is a real loss, not a cosmetic one.
- **Android:** it said "The coach ended the session without a record", but "Save memory" was still
  enabled and did the same thing.

## Steps to reproduce

1. Start a GROW session and press **End** (or let a failing provider drop the closing turn).
2. The closing card appears with nothing in it.
3. Press **Save memory**. The goal's memory is now empty; the previous session's record is gone.

## Root cause

Both cards treated "no record" as a rendering variation rather than as a state with different
actions. Save was never gated on there being something to save.

## Fix approach

Say what happened, disable the save, and keep the way of asking for a record available.

## How to verify fixed

End a session immediately after starting it. The card says the coach wrote no record, "Save memory"
is disabled, the quiet button reads **Close** rather than "Don't save", and the revise field is
present so a record can still be asked for.

## Resolution

Fixed 2026-09-08, both surfaces, with a test each: `src/components/ai/grow-end-card.test.tsx` and
`GrowEndCardTest.kt`.

- The subtitle changes: *"The coach wrote no record of this session. Ask for one below, or close
  without saving."*
- The preview always renders, showing *"No record was written."* in muted italics when there is none
  — an empty box says nothing at all.
- **Save memory** is disabled while the record is blank, so a blank memory can no longer be written
  over a real one.
- The web's revise field is no longer hidden in the empty case; it is what turns this card from a
  dead end into a request.
- The quiet button reads **Close** when there is nothing to discard.

The user commits manually.
