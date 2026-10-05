# The GROW coach saves agreed next steps into Reality, not Will do

- **ID:** BUG-070
- **Status:** ✅ Fixed
- **Reported by:** Claude, running two live GROW sessions at the owner's request, 2026-09-02
- **Area:** AI proposal generation (`src/components/ai/AiPanel.tsx`, `KIND_META` /
  proposal `kind: "action"`) — likely the AI system prompt / GROW-session instructions, not the
  rendering code
- **Severity:** High — the app's own "Will do" tab, which exists specifically to hold committed
  next actions, stays empty after a GROW session whose entire point was to produce commitments

## Summary

During a GROW session on the goal "Build a sustainable morning routine", I explicitly asked:
*"Can you add these as actual will-do items on this goal so I don't lose them?"* By the end of the
session, the three agreed commitments ("Order a separate alarm clock", "Move phone charger to the
kitchen by 10pm tonight", "Place the alarm clock across the room…") were saved as proposals of
**`kind: "action"`**, badge-labelled **"Current action"** (`AiPanel.tsx:3728`), which land in
**Reality → Actions taken** — the GROW section for *"what have you tried?"* (past tense), rendered
with a checkmark icon as if already done.

Meanwhile **Will do → Targets**, the section literally named for future commitments
("Targets are how you execute. Add a numeric, binary, or checklist target."), stayed completely
empty through both sessions, even on the second session where the commitments were reframed as a
concrete, measurable "one-week experiment" (which is exactly the shape a target is meant for).

For the user this reads as: the goal now shows three things as already accomplished ("Current
action") that have not actually been done yet, and the one place designed to hold "what will I
do" has nothing in it.

## Steps to reproduce

1. Open any goal, start a GROW session.
2. Walk the conversation to a concrete set of next steps (e.g. 2-3 specific, dated actions).
3. Ask the coach to save them, optionally using the word "will-do" explicitly.
4. Finish the session and save the proposed changes.
5. Check **Reality → Actions taken** (populated, past tense, checkmark icon) vs. **Will do →
   Targets** (still shows the empty-state copy).

## Root cause

The AI is choosing `kind: "action"` (→ Reality) for what are, by content, future targets. This
could be:
- the GROW-session system prompt not distinguishing "reflect on what you'll try" (Reality/action)
  from "commit to a measurable target" (Will do/target) clearly enough, or
- the `action` proposal kind being the path of least resistance for "add a short text commitment"
  since a `target` proposal likely requires more structure (numeric/binary/checklist type,
  possibly a deadline) that the coach doesn't have readily available mid-conversation.

Not conclusively diagnosed — needs someone with access to the AI system prompt / tool-definition
for GROW sessions to check how `action` vs. `target` proposals are described to the model.

## Fix approach (proposed)

- Tighten the system prompt / tool description so that a concrete, forward-looking commitment
  (has an owner, a rough deadline, and is meant to be tracked to completion) maps to `kind:
  "target"` (simplest form: a binary/checklist target), while `kind: "action"` is reserved for
  reflecting on what has *already* been tried.
- Consider explicit few-shot guidance in the GROW-session prompt distinguishing the two, since the
  whole point of the Will-do phase (per `specs/tech-stack.md`'s GROW model) is to hold exactly this
  kind of commitment.

## How to verify fixed

- Repeat the reproduction steps; the resulting commitments should appear under **Will do →
  Targets**, not **Reality → Actions taken**.

## Resolution

Fixed 2026-09-02. Confirmed root cause: `AiChatService.java`'s `propose_goal_change` tool
schema — the ONLY description the model receives for `kind='action'` was *"add a reality item"*,
with no mention of tense, and `kind='target'` was described purely mechanically ("add a target")
with no connection to the coaching method's own concept of a session's **commitment** (named
explicitly in the "ENDING THE SESSION" section: "one concrete next step the client has committed
to"). Nothing in the prompt ever told the model that a fresh commitment belongs under `target`
rather than `action` — the two were listed side by side as if symmetric.

Fix, in `AiChatService.java`'s `proposalInputSchema()`:
- `'target'/'task'` now opens by explicitly stating it is for a **future commitment**, names the
  session's "commitment" concept, and says outright not to use `'action'` for one.
- `'obstacle'/'action'` was split into two entries: `'obstacle'` unchanged, `'action'` now states
  plainly that Reality is the **past/current** state and a fresh commitment is never an action.
- The "ENDING THE SESSION" instructions now say explicitly: if `end_session`'s `commitment` field
  names a real next step, also propose it as `kind='target'`, never `kind='action'`.

Verified live, before and after, same exact test session (goal "Build a sustainable morning
routine", commitment "drink a full glass of water right after waking up, starting tomorrow"):
before the fix the proposal card read **"CURRENT ACTION"**; after restarting the backend (a Java
prompt change needs a restart — `spring-boot:run` has no devtools hot-reload here) the identical
message produced a card reading **"NEW TARGET"** with a deadline, an "Add target" button, and,
once accepted, the item appeared correctly under **Will do** (not Reality → Actions taken).

Files changed: `backend/src/main/java/com/spiramindscape/backend/ai/chat/AiChatService.java`.
Verified: `mvnw compile` and `mvnw test -Dtest=AiChatServiceAgenticLoopTest` (2/2) pass. This is a
prompt-engineering fix — there is no automated test that pins model behavior itself (it would need
a live provider call and is inherently non-deterministic); the live before/after reproduction above
is the verification.
