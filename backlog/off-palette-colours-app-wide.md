# Colours outside the brand palette are still used across the app

- **ID:** BUG-024
- **Status:** 🐞 Open
- **Reported by:** User
- **Area:** Web frontend (React SPA)
- **Severity:** Low (cosmetic, but it undermines the brand rules in CLAUDE.md)

## Summary

CLAUDE.md lists the **only** colours allowed in product UI (Guava, Kale, Ginger, Parsnip, Salt,
White) and says explicitly: *"Do not invent intermediate shades; if you need a colour, pick one of
these."* The codebase still contains roughly **50 distinct literal hex values**, most of them
outside that list.

On 2026-08-02 the goal-workspace surfaces (targets, tasks, options, the Reality "Actions taken"
panel) were brought onto the palette. The rest was deliberately left for a separate pass, because
two of the offenders are used app-wide and swapping them in one place only would make the app
inconsistent with itself.

## What is left, in priority order

1. **`#ea580c` (orange) — the biggest one.** Used in 8 files: `ProgressBar`, `Section`, `AppShell`,
   `router`, `GoalsTable`, `routes/index`, `routes/goals.$goalId` (nav + page-scroll bar) and the
   "Add target" button. It is the app's de-facto accent, so this is a **design decision, not a
   find-and-replace**: Guava-600 `#EF523C` (keeps it warm) or Kale-500 `#0A8080` (CLAUDE.md's
   "working primary" for fills). Decide once, then apply everywhere.
2. **The dialogs' red** — `#d13239` / `#b0292f` in `ConfirmDialog` and `GoalCard`. The documented
   mapping is destructive → **Guava-600 `#EF523C`** (hover Guava-500 `#F45D48`); `OVERDUE_RED` in
   `Targets.tsx` has already moved there, so the dialogs are the last holdouts.
3. **The login / marketing screen** — `#083f3a` (98 uses), `#006d67` (83), `#005b56` (22): a
   dark-teal surface with no palette equivalent. Needs a decision on whether the login screen is
   in scope for the brand rules at all (it may be deliberately off-brand).
4. **Everything else, mechanical**: `#e6e4df`, `#eef1f0`, `#e7f3f1`, `#f4f5f5`, `#d9dddc`,
   `#cfd6d4` (warm/cool greys → Salt or Parsnip), `#7c3aed`, `#3b82f6`, `#0c69a3`, `#bae2fd`,
   `#e9d5ff`, `#faf5ff`, `#f0f9ff` (resource-type accents in `Resources.tsx` → Kale/Guava tints),
   `#fef3c7`, `#fde68a`, `#fff2a8`, `#92400e`, `#f0b860`, `#d99a4e` (warn tones → Ginger),
   `#15803d`, `#b7e4c7`, `#5fd0a8`, `#f0fdf4` (greens → the functional `success` token, which
   CLAUDE.md does allow), `#4285f4` / `#ea4335` / `#34a853` / `#fbbc05` (Google brand colours on
   the sign-in button — these should **stay**, they are Google's, not ours).
5. **The `--accent` / `--sidebar-accent` tokens** in `src/styles.css` still hold the old
   `oklch(0.95 0.032 180)` that `--primary-soft` used to have; `--primary-soft` is now exactly
   Kale-200 `#E0F2F5`. Decide whether the menu-hover tint should match it.

## Steps to reproduce

`rg -o '#[0-9a-fA-F]{6}' src --type tsx | sort | uniq -c | sort -rn` — compare each value against
the palette table in CLAUDE.md.

## Root cause

Colours were written as literal hexes at the point of use instead of going through tokens, and the
palette rule in CLAUDE.md was added after much of the UI already existed.

## Fix approach

1. Decide the two design questions above (the orange, and whether the login screen is in scope).
2. Move the survivors into **tokens** in `src/styles.css` (e.g. `--accent-warn`, `--type-note`)
   rather than re-literalising them, so the next sweep is a one-file change.
3. Replace per surface, checking each screen visually — several of these are load-bearing status
   colours (overdue, warning, success) where a wrong swap hides meaning.

## How to verify fixed

- The hex inventory above returns only palette values, the functional `success` green, and
  Google's brand colours on the sign-in button.
- A screenshot pass over the dashboard, the goal workspace, the AI panel, the note editor and the
  calendar shows no colour that isn't in CLAUDE.md.

## Resolution

_(empty — open)_
