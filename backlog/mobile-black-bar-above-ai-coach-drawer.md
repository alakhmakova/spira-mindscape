# A black bar (off-palette) renders above the AI Coach drawer on mobile web

- **ID:** BUG-071
- **Status:** ✅ Fixed
- **Reported by:** Claude, testing mobile web at a real 390×844 viewport (Playwright, iPhone UA)
  at the owner's request, 2026-09-02
- **Area:** Frontend, AI chat bottom drawer (web) — `src/components/ai/AiPanel.tsx` and/or the
  drawer host in `src/components/ui/drawer.tsx`
- **Severity:** Medium — visual only, but violates the documented sheet/colour spec and is
  reproducible every time the drawer opens on a real mobile viewport

## Summary

On a real mobile-width viewport (390×844, not the desktop window merely narrowed), opening the AI
Coach from a goal page renders a solid **black** strip across the full width, above the drawer's
teal head ("spira ai coach / New chat / ×"). The page's normal header controls (search icon,
account avatar) are visible on top of this black strip at reduced/no styling. Before the drawer
opens, the same area is the normal teal app header — the black strip only appears once the sheet is
open, and it does not go away after the open animation settles (reproduced with a 1.5s wait after
open — not a transient animation frame).

Pure black is not part of the app's colour palette (`CLAUDE.md` → Brand → Colour lists the only
allowed colours; black is not among them), and this directly contradicts the sheet spec's "no
hairline / no stray chrome above the teal head" rule (`CLAUDE.md` → Components and chrome → 3e).

## Steps to reproduce

1. Load the app at a genuine mobile viewport (390×844 or similar — resizing a desktop Chrome
   *window* is not equivalent; use device emulation, e.g.
   `npx playwright` with `viewport: {width: 390, height: 844}, isMobile: true`).
2. Open any goal.
3. Tap "Coach" (or the AI coach entry point) to open the bottom drawer.
4. Observe the top ~50-55 CSS px of the screen: a solid black bar with the desktop header's search
   icon and avatar visible on it, above the drawer's teal head.

## Root cause

Not isolated — needs someone to inspect the drawer's stacking/backdrop in a real mobile viewport
(devtools open on an actual phone or emulator, not just a narrowed desktop window). Candidates:
a scrim/backdrop element painted opaque black instead of the intended dim/transparent treatment,
or a z-index/layout issue that leaves a page-header-height gap above the sheet where the backdrop
shows through at full opacity.

## Fix approach

- Inspect the drawer backdrop styling (likely in `src/components/ui/drawer.tsx`, given
  `CLAUDE.md`'s existing notes on vaul's drawer defaults needing overrides) at a real mobile
  viewport and correct the backdrop colour/opacity so no black shows, and so the drawer's top edge
  meets the constant gap already defined for other sheets (`sheet-top-gap` / `.sheet-*` utilities
  per `CLAUDE.md` → 3e-bis).

## How to verify fixed

- Repeat the reproduction steps; no black should be visible above the drawer's teal head, at open
  and after settling.

## Resolution

Fixed 2026-09-02. Root cause: `src/components/ui/drawer.tsx`'s `DrawerOverlay` used raw
`bg-black/80` as its scrim. The AI Coach drawer sizes itself with `.sheet-h` (per the sheet spec,
`calc(100dvh - var(--sheet-top-gap))`), so its top edge sits a fixed 76px below the viewport top —
and the overlay, correctly, fills that gap. The gap itself is intentional; the colour filling it
wasn't: literal black is not in the palette (CLAUDE.md → Colour).

Same `bg-black/80` literal was also found, identically, in `sheet.tsx`, `dialog.tsx` and
`alert-dialog.tsx` — one shared shadcn default reused unbranded across every overlay primitive in
the app, not a one-off. Fixed all four to `bg-foreground/80`: `--foreground` is the app's existing
near-black ink token (already used everywhere as `text-foreground`), registered as a Tailwind
colour via `--color-foreground: var(--foreground)` in `styles.css`, so `bg-foreground/80` needed no
new token. (Two bespoke, unreported overlays in `Resources.tsx` — an image-preview lightbox — still
use raw `bg-black`; left alone as out of scope for this fix.)

Verified visually: Playwright at a real 390×844 mobile viewport, AI Coach drawer opened — the strip
above the teal head is now a dark teal-charcoal scrim (the near-black foreground token over the
page's own colour), not pure black. `tsc --noEmit` and `eslint` clean on all four changed files.
