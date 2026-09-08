# Dismissing a multi-change proposal review has no confirmation — and its X is easy to hit by mistake

- **ID:** BUG-075
- **Status:** ✅ Fixed
- **Reported by:** Claude, running a live GROW session at the owner's request, 2026-09-02
- **Area:** Frontend AI chat, stepped proposal review card (`SteppedProposalCard` in
  `src/components/ai/AiPanel.tsx`, per `CLAUDE.md`'s "AI proposal cards" spec)
- **Severity:** Medium — silently and irreversibly discards AI-drafted changes that can be the
  entire output of a long conversation

## Summary

At the end of a GROW session, the coach presented a "**2 CHANGES**" review card (progress rail,
`1 / 2`, a per-item checkbox, "Back / Next", "Save all N" / "Dismiss all"). Reaching for what I
expected to be a "next item" control, I clicked the small `×` in the card's top-right corner —
this does **not** skip or dismiss one item, it **immediately discards every pending change** in
the review (shown afterward only as a small greyed "✕ Dismissed" tag in the transcript), with no
"are you sure?" step. In this session that discarded the coach's just-drafted edit reflecting the
entire conversation's outcome, with no way to undo it short of re-running the session.

This is a real risk given the review card's own footer already warns *"Anything you leave
undecided stays waiting in the goal"* — implying the design expects the user to leave a review
pending rather than dismiss it, yet the fastest, most reachable control (top-right `×`, the same
position a "close this card" control occupies elsewhere in the app) does the most destructive
thing available on the card.

## Steps to reproduce

1. Run a GROW session that ends with 2+ proposed changes bundled into one `SteppedProposalCard`.
2. On the review card, click the small `×` at the top-right (next to "N CHANGES" / the step
   counter) instead of "Next" or "Save all N".
3. Observe: all pending changes are immediately discarded, replaced by a "✕ Dismissed" tag, with
   no confirmation prompt.

## Root cause

The card's top-right `×` is wired directly to "dismiss all", with no distinction from a lower-risk
"close/skip" affordance, and no confirmation step for a destructive, non-undoable action —
inconsistent with how other destructive actions in the app are gated (e.g. deleting a goal is
behind a confirm dialog per `CLAUDE.md`'s Goal-workspace navigation section).

## Fix approach

- Either move "dismiss all" behind a confirmation (consistent with other destructive actions in
  the app), or visually separate the top-right `×` from the "N CHANGES" review flow so it reads as
  clearly destructive before it is clicked (e.g. requiring the explicit "Dismiss all" text link
  already present in the footer, and removing/relabeling the corner `×`).

## How to verify fixed

- Repeat the reproduction steps; a single accidental click on the corner control should no longer
  discard all pending changes without an explicit confirming action.

## Resolution

Fixed 2026-09-02. Both the corner `×` and the footer "Dismiss all" link in `SteppedProposalCard`
now open the existing `ConfirmDialog` (the same one goal/target delete already uses — reused
as-is, no new UI shape) instead of calling `dismissAll()` directly: "Dismiss all N changes? … This
can't be undone." / "Yes, dismiss all". `dismissAll()` itself is unchanged and only runs from the
dialog's `onConfirm`.

Files changed: `src/components/ai/AiPanel.tsx`. Verified: `tsc --noEmit` clean, `npm run lint`
clean (no new warnings beyond the project's existing 14), `npm test` 267/267 passing.
