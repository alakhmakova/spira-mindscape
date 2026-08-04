# The whole app has never been audited for accessibility

- **ID:** BUG-023
- **Status:** 🐞 Open
- **Reported by:** User
- **Area:** Web frontend (React SPA) + Android app
- **Severity:** Medium (no single broken screen; an unmeasured, accumulating gap)

## Summary

Spira has never had an accessibility pass. Individual features were built with reasonable
instincts — `aria-label` on icon buttons, `role="textbox"` on inline fields, real `<button>` and
`<input>` elements — but nothing has been **verified** with a keyboard, a screen reader, or a
contrast checker, and there is no automated check in the test suite to stop regressions.

Recent UI work makes the gap more concrete: element menus now **appear only on hover or focus**,
inline fields are custom `role="textbox"` spans that swap to a `<textarea>`, resource references
render as buttons inside running text, and the mobile target card hides its progress controls
behind an expander. Each of those is a place where keyboard and screen-reader behaviour needs to
be checked rather than assumed.

## Steps to reproduce

Not a single reproducible defect — the point is that these paths are unverified. Concrete things
to walk through:

1. **Keyboard only (no mouse), whole goal workspace.** Tab from the top: can every action be
   reached and used — the reality item ⋯ menu, an option card's ⋯ and rating smiley, the target
   padlock, "Update progress", the resource picker, the deadline popover? Is the focus ring
   visible everywhere it lands (several controls only reveal themselves on `:hover` /
   `:focus-within`)? Does focus stay trapped inside dialogs and return sensibly on close?
2. **Screen reader** (NVDA or VoiceOver on the web, TalkBack on Android). Is an inline field
   announced as editable, with its label and its "too long" / "required" messages? Are the
   `{{res:…}}` links announced as links with the resource's name? Is the padlock's state
   (`aria-pressed`) read out? Are toasts (`sonner`) announced at all?
3. **Contrast.** Check the brand pairs against WCAG AA (4.5:1 for body text): muted text on white,
   `text-muted-foreground/60` on the lock, white on Kale-500, Kale-500 on Kale-200 (the
   "Update progress" footer), the dashed "Set deadline" tile, and the Guava overdue text.
4. **Zoom / large text.** 200% browser zoom and Android's largest font size — does the goal page
   still work, or do the floating menus and the deadline tile collide with the text?
5. **Motion and touch targets.** Are tap targets ≥ 44×44 on mobile (the corner badges are 28px)?
   Does anything rely on hover alone, with no touch or keyboard equivalent?

## Root cause

Accessibility was never made part of the definition of done: no audit, no tooling, and no
automated check. `CLAUDE.md`'s UI rules cover brand and layout but say nothing about keyboard or
screen-reader behaviour, so nothing pushes back when a feature ships without it.

## Fix approach

Measure first, then fix — and keep it from regressing:

1. **Add automated checks** (cheap, catches the mechanical half):
   - `axe-core` via `vitest-axe` / `@axe-core/playwright` on the goal workspace, the dashboard and
     each dialog in the existing E2E specs.
   - `eslint-plugin-jsx-a11y` in the frontend lint config.
   - Android: Compose UI tests already render; add Accessibility Scanner / `espresso-accessibility`
     checks on the main screens.
2. **Do the manual passes** listed under "Steps to reproduce" and write findings up as individual
   backlog entries — this file is the umbrella, not the fix.
3. **Fix by class of problem**, most likely: focus-visible styling on the reveal-on-hover controls,
   labels/`aria-describedby` on the inline fields' validation messages, `aria-live` for toasts and
   optimistic-sync messages, contrast corrections inside the allowed palette, and touch-target
   sizes on the corner badges.
4. **Write the rule down** in `CLAUDE.md` (a keyboard + screen-reader line in the UI conventions)
   so it is part of the definition of done rather than a one-off audit.

## How to verify fixed

- The whole goal workspace can be driven with the keyboard alone, with a visible focus indicator
  at every stop.
- `axe-core` reports no violations on the dashboard, the goal workspace, and every dialog, and the
  check runs in CI.
- A screen-reader pass over create-goal → add option → attach resource → update a target announces
  every control's name, role and state.
- All brand text pairs meet WCAG AA contrast at their real sizes.
- `eslint-plugin-jsx-a11y` is enabled and the codebase is clean under it.

## Resolution

_(empty — open)_
