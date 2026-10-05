# A session's proposals were lost when it ended

- **ID:** BUG-080
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-05 — "я провела grow session с Gemini flesh 3.5 … и proposal так
  и не были показаны"; the same in the Cohere session two days earlier
- **Area:** AI chat (`src/components/ai/AiPanel.tsx`,
  `android/app/.../ui/ai/AiChatViewModel.kt`), coach prompt
  (`backend/src/main/resources/prompts/grow/coach-method.md`)
- **Severity:** High — the point of a session is what it changes on the goal, and that is what was
  being dropped

## Summary

A GROW session would run its full length, work something out, and then end with **no proposals at
all** — nothing to review, nothing written to the goal. It happened across providers (Cohere
Command A, Gemini Flash), so it was not one model behaving badly.

Two causes, one on each side of the wire.

**The prompt asked for two different things in one breath.** "Ending the session" told the coach to
write the session record *and* propose goal changes *and* say goodbye, all in the same closing
turn. Models did the first and the last and skipped the middle — a record reads like a summary, so
once it is written the turn feels finished.

**And the client had nowhere to put them anyway.** The ending turn was flagged `ending`, and the
ending path took the record and dropped everything else the turn carried — so on the runs where a
model *did* propose something, the proposals were parsed and then thrown away.

## Steps to reproduce

1. Run a GROW session to its natural end (or press End) with any provider.
2. Watch the closing turn: a record is written and a goodbye is said.
3. No proposal cards appear, and the goal is unchanged — even where the conversation clearly agreed
   on a new target or a changed deadline.

## Root cause

- The prompt merged "the record" and "the proposals" into one instruction (and the memory block was
  itself being read as material for the record).
- Client-side: `const ending = endRecord !== null` meant any turn carrying a record was treated as
  the ending and had its proposals discarded.

## Fix approach

Split the close into explicit steps, and stop the client discarding a turn's proposals.

## How to verify fixed

Run a session in which something concrete is agreed. At the close, the proposals arrive as their
own turn and are reviewable **before** the goodbye; the record describes the session and does not
contain the proposals.

## Resolution

Fixed 2026-09-08.

**Ending is now three steps, in the prompt and in both clients:**

1. the **record** — what this session was about, written for the next one;
2. the **proposals** — asked for as their own dedicated turn (`askForProposals()` on both
   surfaces, sending an instruction that asks for nothing but goal changes);
3. the **goodbye** — only once the review is done.

`coach-method.md` states the split, and its failure table carries a row for "wrote a record and
stopped". `GoalMemoryService.memoryBlock()` now says outright that the memory is *context, never
material for the record*.

On the client, the ending flag is `const ending = endRecord !== null && !proposalsTurn;` — a
proposals turn keeps its proposals even if the model re-calls `end_session`, which is the exact case
a code review caught still losing them (verified red before the fix, green after).

Files: `AiPanel.tsx`, `AiChatViewModel.kt`, `AiChatService.java`, `GoalMemoryService.java`,
`prompts/grow/coach-method.md`. The user commits manually.
