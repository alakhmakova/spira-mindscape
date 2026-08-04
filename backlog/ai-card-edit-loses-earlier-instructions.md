# A card's "Edit" loses earlier instructions, and the request never appears in the chat

- **ID:** BUG-026
- **Status:** ✅ Fixed
- **Reported by:** User
- **Area:** Web frontend (React SPA) — AI assistant panel, proposal cards
- **Severity:** High (the user's earlier correction was silently discarded)

## Summary

The **Edit** button on a proposal card (next to Accept) opens a "Type a change for the AI…" box.
Two problems:

1. Ask for change #1, then change #2 → only #2 survived. The AI behaved as if it had never heard
   the first request.
2. The request itself was never shown anywhere: no bubble in the chat, no record after the fact.

## Steps to reproduce (before the fix)

1. Open a goal → **ai coach** → ask it to write/extend the goal description.
2. On the card press **Edit** → "add a sentence about Friday reviews" → the card updates.
3. Press **Edit** again → "mention that the owner is a beginner".
4. The new proposal has the beginner mention but the Friday sentence is gone. Neither request is
   anywhere in the transcript.

## Root cause

`reviseInPlace` in `src/components/ai/AiPanel.tsx`:

- The prompt it built described the current proposal with its **card display strings** —
  `proposalDisplay(p).headline` + `detail`, which are deliberately clipped (`truncate(…, 40..64)`)
  and blind to everything outside `title`: a note's `body`, a checklist's `items`, the numeric
  `total/current/unit`, `deadline`, resource `patch` fields. The model therefore re-proposed from
  a lossy summary, and a re-proposal is a full replacement — whatever the summary didn't show came
  back missing. (For a checklist target the items were never sent at all.)
- The instruction was sent to the model but never written to the transcript, so it was invisible
  to the user *and* absent from the history replayed on the next turn.

## Fix

- New pure helpers in `src/components/ai/proposal-logic.ts`:
  - `proposalContext(p)` — every populated field of a proposal as labelled lines, the value
    **unclipped**, capped at 4000 chars. This is what the revise prompt now sends, together with
    "repeat every field you are not changing".
  - `buildHistory(msgs)` — the one place that turns the transcript into model history (it replaced
    three copies of the same filter+map), and it **merges consecutive same-role turns**:
    `AiChatService.buildMessages` replays history verbatim and no provider adapter normalises
    roles, so a cancelled/failed revise must not leave two user turns in a row.
- `reviseInPlace` now writes the exchange into the chat: a **user bubble** with the instruction
  verbatim, captioned `Change to «<card>»` (new `Msg.revisedLabel`), then the AI's reply — its own
  text when it wrote any, otherwise `Updated «<card>».` A failed or timed-out revise appends the
  usual error bubble instead of a toast. An explicit Cancel stays silent.

## How to verify fixed

1. Ask the coach for a goal description; **Edit** → "add a sentence about Friday reviews";
   **Edit** again → "mention that the owner is a beginner". The result contains **both**.
2. Same on a checklist target create: "add a fourth task", then "rename the first task" — the
   fourth task is still there.
3. Both requests are readable in the chat, each captioned with the card's name, each followed by a
   reply; they survive a reload (they are part of the persisted, synced transcript).

## Resolution

Fixed on 2026-08-03. Covered by unit tests for `proposalContext` and `buildHistory` in
`src/components/ai/proposal-logic.test.ts`, by a stubbed E2E asserting that the second revise's
request body carries the first revision's full text, and verified against the real provider for
both a goal description and a checklist target.
