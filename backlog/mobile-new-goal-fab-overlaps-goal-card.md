# The "New goal" FAB overlaps a goal card on mobile web

- **ID:** BUG-072
- **Status:** ✅ Fixed
- **Reported by:** Claude, testing mobile web at a real 390×844 viewport (Playwright, iPhone UA)
  at the owner's request, 2026-09-02
- **Area:** Frontend, All-goals list (`src/routes/index.tsx`) — the fixed "New goal" FAB
  (`aria-label="New goal"`, `fixed bottom-5 right-5`)
- **Severity:** Low — cosmetic overlap, but it sits on top of a real tap target ("Start")

## Summary

On the All-goals list at a real mobile width, the fixed-position "+" FAB (bottom-right, opens the
New goal sheet) visually overlaps the "Set deadline / Start →" row of whichever goal card happens
to fall at that scroll position — in testing, the third card in the list. The FAB covers part of
the card's text and part of its "Start" tap target.

## Steps to reproduce

1. Load the All-goals list at a real mobile viewport (390×844).
2. With enough goals to fill the screen (the local dev DB has 14 — see the related data-hygiene
   note), take a full-page screenshot or simply scroll: the FAB sits fixed at `bottom-5 right-5`
   and a card's "Start" row lands directly under it.

## Root cause

The FAB is `position: fixed`, unconditionally `bottom-5 right-5` (`sm:bottom-7 sm:right-7`) with no
corresponding bottom padding/margin reserved on the goal list, so on a screen short enough (or a
card tall enough) the FAB's 56×56px circle lands on top of card content instead of in the page's
own empty margin.

## Fix approach

- Add bottom padding to the goals list (at least the FAB's diameter plus its offset, on mobile)
  so the last visible row can never sit under the fixed FAB, matching how other fixed-footer
  screens in the app already reserve space for their own chrome.

## How to verify fixed

- Repeat the reproduction steps; no card's text or "Start" control should render underneath the
  FAB at any scroll position.

## Resolution

Fixed 2026-09-02. `src/routes/index.tsx`: the list container's `py-8 sm:py-12` became
`pt-8 pb-24 sm:pt-12` — `pb-24` (96px) clears the FAB's 56px circle plus its `bottom-5`/`bottom-7`
offset at every breakpoint. Verified with Playwright at a real 390×700 mobile viewport, scrolled to
the actual bottom (a `fullPage` screenshot is not a reliable check here — it renders a `position:
fixed` element frozen at its mid-scroll capture position, which looks like an overlap regardless of
the fix): the last card now clears the FAB with visible whitespace below it.
