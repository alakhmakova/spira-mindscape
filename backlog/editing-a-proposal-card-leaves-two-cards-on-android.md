# Editing a proposal card leaves two cards on Android, and the new one says "Dismissed"

- **ID:** BUG-047
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-24 — "сейчас по крайней мере на андроид я вижу что остаётся
  висеть старая карточка, а под ней появляется исправленная — карточка должна быть только 1 всегда
  и на вебе и на андроид"
- **Area:** Android (`ui/ai/AiChatViewModel.kt`, `ui/ai/AiChatScreen.kt`)
- **Severity:** High — the correction the user has just asked for is on screen, labelled as
  refused, and cannot be accepted at all

## Summary

Tap **Edit** on a proposal card, type a change, send it. The revised proposal comes back as a
**second** card: the original is still waiting in the footer, and the new one sits in the
transcript above it drawn as a muted **"Dismissed"** pill. Nothing was dismissed. The only way
forward is to dismiss the card you have just corrected, at which point the correction finally
takes the footer.

Two separate faults, one on top of the other.

## Steps to reproduce

1. Open a goal on Android, open the AI coach, ask for something the coach can propose —
   "Create a note titled Interview prep".
2. On the card, tap **Edit**, type "make it about SQL", tap **Send to AI**.
3. The old card is still in the footer with Accept / Edit / Dismiss. Scroll up: the revised
   proposal is in the transcript as a "Dismissed" pill.

## Root cause

**1. The revision was an ordinary turn.** `reviseProposal` built a prompt and called
`send(...)`, which appends a new assistant message carrying whatever proposals come back. The
original proposal was never touched, so it stayed `PENDING`. The web has never worked this way —
`reviseInPlace` (`AiPanel.tsx`) swaps the answer into the original card's slot, keeping its id.

**2. The result line stood in for a card that was still waiting.** Only the **first** pending
message gets the footer (`pendingMessage = messages.firstOrNull { … }`), and the transcript row
rendered its compact `ResultSummary` whenever the message was not that one. `ResultSummary` shows
"Dismissed" when nothing in the message is *approved* — which is true of a proposal that is still
pending. So the second pending card was drawn as refused. The web gates the same line on
`!m.proposals.some(pr => pr.status === "pending")`, which is the correct condition.

A third thing was visible in the same screenshot and is fixed with it: the transcript showed the
**raw prompt** ("Revise this proposal: kind: note / title: … / target type: binary") as the user's
chat bubble. That scaffolding is for the model. The web writes only the user's own words, captioned
with the card being changed.

## Fix approach

`reviseProposal(messageId, proposal, instruction)` now mirrors `reviseInPlace`:

- writes the **instruction** into the transcript with `revisedLabel = proposal.title`;
- **rejects the superseded server row**, so it cannot come back later through the pending-proposal
  restore path;
- sends the model the whole current proposal (`proposalContext`) and asks it to repeat every field
  it is not changing, because the re-proposal *replaces* the old one;
- swaps the first returned proposal into the original card's slot, **keeping its id**, with any
  extras joining the same message so it stays one card group;
- leaves the card untouched when the model answers with words instead of a proposal.

And `AssistantTurn` gates its result line on "nothing here is still pending" rather than on "this
message's card is in the footer". `cardInFooter` is gone; it no longer means anything.

## How to verify fixed

`ui/ai/ProposalCardLifecycleTest` drives the real screen through Accept, Dismiss and Edit against a
scripted transport, and asserts after an Edit that exactly one card is on screen, that it is the
revised one, that its proposal id is the original's, that the superseded server row was rejected,
and that the transcript carries "make it about SQL" and not "Revise the resource note you
proposed." The Edit case was confirmed **failing** on the old code before the fix went in.

Its web twin is `e2e/ai-proposal-card.spec.ts`, which stubs the SSE stream and checks the same
three flows in the browser.

By hand, on the emulator: ask for a note, Edit it, and count the cards.

## Resolution

Fixed 2026-08-24. Verified on `emulator-5554` against the local backend: after "call it Employer
research" the panel shows one card, "Employer research", with the transcript reading
*Change to «Company research»* → *call it Employer research* → *Updated «Employer research».*
