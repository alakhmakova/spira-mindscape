# A long AI proposal (rewritten goal description) overflows the chat panel — Accept/Dismiss unreachable

- **ID:** BUG-025
- **Status:** ✅ Fixed
- **Reported by:** User
- **Area:** Web frontend (React SPA) — AI assistant panel
- **Severity:** High (the proposal could neither be accepted nor dismissed, blocking the chat)

## Summary

Asking the coach to extend a goal's description produced a proposal card whose headline was the
**entire new description**. The card grew taller than the panel, nothing scrolled, and the
Accept / Edit / Dismiss row sat below the visible area — so the user could not resolve the
proposal at all (and the chat stays blocked while a proposal is pending).

## Steps to reproduce (before the fix)

1. Open a goal, open **ai coach**.
2. Ask it to extend / rewrite the goal description ("допиши описание цели").
3. The reply proposes an edit; the card fills the panel and continues past its bottom edge.
4. Try to scroll the card — nothing moves. Accept and Dismiss are unreachable.

## Root cause

Two independent problems, both in `src/components/ai/AiPanel.tsx`:

1. **The whole text was the headline.** `proposalFromToolArgs` puts the new text in
   `Proposal.title` for `kind: "edit"`, and `proposalDisplay` had no `case "edit"`, so it fell
   through to `headline = p.title` — printed in full, at 17px, unclipped. (Every other long-text
   kind — `note`, `edit_note`, `new_goal` — already routes its text through `body`, which the card
   shows as a "Read full content" button.)
2. **The pending-card area could not scroll.** The card renders in the panel's footer inside
   `<div className="px-3 pb-3 pt-1 shrink-0">`. In a flex column, `shrink-0` with no
   `max-height` means the child simply overflows the panel — there is no scroll container to
   scroll.

## Fix

- New pure helper `editDisplay` in `src/components/ai/proposal-logic.ts`: a `description` edit is
  shown as headline **"New description"** + a ≤120-char single-line preview, with the full text
  moved into `body` so the existing "Read full content" modal (bounded, scrollable, Markdown)
  opens it. A `title` edit keeps its headline, clipped at 120 chars.
- The pending-card wrapper in `AiPanel.tsx` is now `max-h-[60%] overflow-y-auto` — a safety net so
  any tall card (a stepper, a 12-item checklist create) scrolls to its buttons instead of running
  off the panel.

## How to verify fixed

1. Ask the coach to rewrite a goal description at length.
2. The card is compact: "GOAL EDIT" → **New description** → a two-line preview → **Read full
   content** → Accept / Edit / Dismiss, all visible on a 390×780 mobile viewport.
3. "Read full content" opens the complete text in the scrollable modal; Accept saves the full
   description (not the preview).
4. Propose a create with a dozen checklist items: the card area scrolls and its **Add target** /
   **Dismiss** buttons are reachable.

## Resolution

Fixed on 2026-08-03. Covered by four unit tests for `editDisplay` in
`src/components/ai/proposal-logic.test.ts` (preview length, newline collapsing, title edits
untouched, over-long title clipped) and verified visually on desktop (1280×800) and mobile
(390×780) with a stubbed AI stream.
