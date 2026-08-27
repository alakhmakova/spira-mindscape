# The "Open" link on an accepted proposal is white on the light end of the chat

- **ID:** BUG-048
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-08-24 — "на вебе цвет слишком бледный на светлом фоне экрана, нужно
  подобрать более подходящий по accessibility цвет"
- **Area:** Web (`src/components/ai/AiPanel.tsx` — `ResultSummary`)
- **Severity:** Medium — the only route to the thing the assistant has just created is invisible

## Summary

Accept a proposal in the web coach. The card is replaced by a compact result line — a tick, what
was saved, and an **Open** link to it. That line was drawn in `text-white/80` with the link in
`text-white`, on a conversation area that fades **from teal down to `#F2FFFF`**. A just-answered
card lands at the bottom of the transcript, which is exactly where the background is nearly white,
so the line and its link disappear.

Measured, not eyeballed: the Open link scores **1.02:1** against `#F2FFFF`. The threshold for body
text is 4.5:1.

The **Android twin was already right** — `CHAT_INK` (`#003737`) on a 72%-white pill with the Open
mark in Kale — so this is one of the rare cases where the web copies Android rather than the other
way round.

## Steps to reproduce

1. Open a goal in the web app, open the coach, ask for a note.
2. Accept the card.
3. Scroll to the result line. On a short conversation it is legible; on a long one, where the
   gradient has faded out, it is not.

## Root cause

The AI panel's root is `text-white`, which is right for the Kale chrome and wrong for the chat area
underneath it. Every other element in the conversation sets its own dark ink (`text-[#003737]`);
`ResultSummary` was the one that did not, and it is the one component that only ever appears after
an interaction, so it was never looked at on a full screen.

## Fix approach

`ResultSummary` now draws the pill the way Android does: `text-[#003737]` on `bg-white/70`, the
tick and the Open link in Kale `#0A8080`, and the muted "Dismissed" variant in `#003737`/60 on the
same plate. That pair holds at **both** ends of the gradient — ≈10:1 over the teal, ≈13:1 near
white — so there is no part of the conversation where it fades.

## How to verify fixed

`e2e/ai-proposal-card.spec.ts` reads the computed colour of the result line and of its Open link
and **computes the contrast ratio** against `#F2FFFF`, the gradient's lightest stop, failing under
4.5:1. Reverting the class to `text-white` makes it fail with
"its Open link is rgb(255, 255, 255), which is 1.02:1" — checked before the fix shipped.

A screenshot would not have caught this: a downscaled capture has misled twice already on this
project (a toast tick read as "green", a nav item as "teal"), which is why the assertion reads
`getComputedStyle` instead.

## Resolution

Fixed 2026-08-24, alongside BUG-047 — the two came out of the same pass over what a proposal card
leaves behind once it is answered.
