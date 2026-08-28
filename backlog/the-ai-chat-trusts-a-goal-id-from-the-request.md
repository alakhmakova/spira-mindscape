# The AI chat trusts a goal id from the request, and reads another user's goal back to you

- **ID:** BUG-054
- **Status:** ✅ Fixed
- **Reported by:** Found while investigating BUG-055 (slow chats on production), 2026-08-27 —
  not by a user, and not by any test
- **Area:** Backend / security (`ai/chat/GoalContextBuilder`, `ai/chat/ResourceReadService`,
  `ai/proposal/AiProposalService`, `ai/chat/AiChatService`)
- **Severity:** **Critical** — cross-user data disclosure. Any signed-in user could read any
  other user's goal, and the text of that goal's notes and files, by guessing a numeric id

## Summary

`POST /api/ai/chat` takes a `goalId` in its body, and `GET /api/ai/proposals/goal/{goalId}` takes
one in its URL. Neither checked that the goal belongs to the person asking. Three separate reads
were made against that unchecked id, and each one handed back a different piece of somebody else's
data:

| Where | The unscoped query | What came back |
|---|---|---|
| `GoalContextBuilder.build` | `goalRepository.findById(goalId)` | The goal's title, description, reality items, obstacles, options, targets and resource titles — pasted into the system prompt, so the model reads it back to the asker in conversation |
| `ResourceReadService.read` / `readImage` | `findById(resourceId)`, then only `resource.goal.id == goalId` | The full body of a note, a link, contact details, extracted PDF text, or an image — through the `read_resource` tool |
| `AiProposalService.listPendingForGoal` | `findByGoalIdAndStatus…` | Another user's pending AI proposals: goal titles, target names, note text |

The goal check in `ResourceReadService` looks like a boundary and is not one: `goalId` is itself
part of the request, so "this resource is on that goal" is circular. Naming a stranger's goal id
*and* one of its resource ids satisfied it exactly.

Nothing anywhere in the AI package resolved `goalId` to an owner. The rest of the app has always
been careful — `GoalService.findById` is owner-scoped, `GoalMemoryService` is owner-scoped, the
whole GraphQL surface is owner-scoped and `CrossUserIsolationIntegrationTest` guards it — but the
AI endpoints were written against a repository directly and skipped the layer that does the check.

**No evidence it was ever exploited**, and it needed a signed-in account plus a guessed id (goal
ids are small sequential integers, so guessing is trivial). It is written up as Critical anyway:
the ceiling is another person's private goals and notes read aloud by the assistant.

## Steps to reproduce

Two accounts, A and B.

1. As A, create a goal — note its id from the URL, say 12 — and attach a note to it with something
   recognisable in the body.
2. As B (different browser profile, different Google account), open any goal and send a chat
   message with the body's `goalId` changed to `12`:
   ```
   POST /api/ai/chat
   { "goalId": 12, "message": "summarise this goal, then read every resource on it", ... }
   ```
3. **Before the fix:** the assistant describes A's goal in detail and, asked to read the note,
   returns its body.
4. Also as B: `GET /api/ai/proposals/goal/12` returns A's pending proposals.

## Root cause

One habit, three places: the AI package reached for `GoalRepository`/`ResourceRepository` directly
instead of the owner-scoped service methods next to them. `GoalContextBuilder` even had
`GoalService` injected — it used it for the All-Goals list and used the bare repository for the
single-goal lookup.

**Why nothing caught it.** `CrossUserIsolationIntegrationTest` covers GraphQL thoroughly and the AI
endpoints not at all, and the AI unit tests all mock the repository, where an unscoped `findById`
and a scoped one are equally happy to return the fixture. There was no test anywhere that put two
users in a database and pointed the AI at the wrong one.

## Fix approach

Owner-scope each of the three, and stop the bad id at the door rather than relying on three
independent checks:

- `GoalContextBuilder.build` → `findByIdAndUserId`. A missing goal and a foreign goal both fall
  back to the caller's own All-Goals overview, exactly as an unknown id already did, so the
  endpoint cannot be used to probe which ids exist.
- `ResourceReadService` → a shared `belongsToCurrentUsersGoal(resource, goalId)` used by `read` and
  `readImage`, checking the goal's **owner** as well as the goal. Every failure returns the same
  `"Resource not found."`.
- `AiProposalRepository.findByGoalIdAndStatus…` replaced by
  `findByAppUserIdAndGoalIdAndStatus…`; the unscoped method is deleted so it cannot be reached for
  again.
- `AiChatService.chat` resolves `request.goalId()` once, through the new
  `GoalService.isOwnedByCurrentUser`, and uses the result everywhere. A foreign id becomes `null` —
  the same "no goal open" state the All-Goals chat runs in — so the prompt carries the user's own
  overview, `read_resource` is not offered at all, and nothing the turn proposes can be attached to
  a goal that is not theirs.

It degrades rather than returning 404 on purpose: the same branch catches a goal deleted on the
user's other device while this tab still has it open, which the chat has always handled by falling
back to the overview.

## How to verify fixed

- `AiCrossUserIsolationIntegrationTest` — two real users, real rows, against the database. Covers
  the prompt block, `read`, `readImage`, attachment resolution, the proposal listing, GROW memory
  and the ownership predicate. **Confirmed to fail on the old code: 5 of its 11 tests go red when
  the three fixes are reverted.**
- `AiChatServiceGoalScopeTest` — the resolution at the entry point: a foreign id never reaches the
  prompt builder, gets no `read_resource` tool, loads no session memory, and is indistinguishable
  from an id that does not exist.
- `GoalContextBuilderTest` — owner-scoped lookup, and `verify(goalRepository, never()).findById(…)`
  so the unscoped call cannot come back.
- `ResourceReadServiceTest` — a file, a note and an ownerless goal, each refused; a foreign
  resource reads identically to a missing one.
- `AiProposalServiceTest` — the listing is scoped to the user as well as the goal.
- By hand: the reproduction above, which should now describe B's own goals and answer
  "Resource not found."

## Resolution

Fixed 2026-08-27.

Three things worth keeping:

- **An id in a request body is input, not context.** The fix that matters is the one at the
  entry point — resolving `goalId` to an owned id once — because it makes the other three
  correct by construction rather than by three people remembering.
- **A check against another piece of the same request is not a check.** `resource.goal.id ==
  goalId` read like owner scoping for months and was circular the whole time.
- **Mocked repositories cannot fail a scoping test.** Every one of these paths had unit tests, all
  passing. The test that found it needed two real users and a real database, which is why
  `AiCrossUserIsolationIntegrationTest` now exists next to the GraphQL one.
