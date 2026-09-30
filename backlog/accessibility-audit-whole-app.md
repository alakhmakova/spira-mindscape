# The whole app has never been audited for accessibility

- **ID:** BUG-023
- **Status:** 🔧 In progress — the automated layer is in (2026-09-29); the manual passes are not
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

## Progress — 2026-09-29: the automated layer

**`e2e/accessibility.spec.ts`** runs axe-core over four screens through the Playwright suite that
already existed — the dashboard, the goal workspace, the "New goal" sheet and the vacancy map
panel — against the WCAG 2.1 A + AA rule tags. It runs in CI with every other spec
(`npm run test:e2e`), so nothing extra had to be wired into the workflow.

**What it is worth, honestly.** Automated rules cover roughly a third of the criteria. axe can see
that a button has no accessible name; it cannot see that the name is wrong, that the focus order
is nonsense, or that a control which only appears on hover cannot be reached at all. A green run
is not conformance — it is "the machine-checkable part has not got worse". Everything under
"Steps to reproduce" above still has to be done by hand.

### What the first run found, and what was done about it

**Fixed on the spot — `button-name`, critical.** The resource card's chevron ("show this card's
actions") and the chevron that closes it again were icon-only `<button>`s with no accessible name:
a screen reader announced them as "button", on every screen that lists resources. They now carry
`aria-label` ("Actions for <the resource's name>" / "Hide the actions").
Two E2E specs had to become `exact: true` afterwards — with the card and its actions button both
named after the file, `getByRole("button", { name: "sample.png" })` matched two elements.

**Recorded as a baseline — `color-contrast`, serious.** Three brand pairs fail AA, and each is a
design decision rather than a defect in one screen, so the spec accepts them **by rule and by
count per screen** (a new instance of the same rule still fails) until they are decided:

| Pair | Measured | Needs | Where |
|---|---|---|---|
| White text on the header's teal `#268B8A` at 14px | **4.08:1** | 4.5:1 | the app header, every screen |
| Guava `#F45D48` as text on white at 13px | **3.22:1** | 4.5:1 | the overdue line on a target |
| `text-muted-foreground/60` `#A3A3A3` on white at 16px | **2.52:1** | 4.5:1 | muted labels on the goal page |

None of the three is close, and the last one is a long way off. The options are the same in each
case: darken the foreground, enlarge the type past the large-text threshold (18.5px, or 14px
bold, where 3:1 is enough), or accept the failure knowingly. The first is a palette change and is
the owner's call — which is exactly why they are parked here rather than patched.

`ACCEPTED` in the spec is meant to shrink: fix one, lower its number, and the check holds the new
line.

## Found by hand — 2026-09-30: delete came before the goal it deletes

**The owner found it with a keyboard in five minutes**, which is the point of the manual passes:
no automated rule can judge focus order, and axe had called the dashboard clean.

Measured in the browser, tabbing from the top of All goals:

```
... Cards | Timeline | Calendar | Delete goal | Get a job as a Software Developer | Set deadline
  | Start | Delete goal | Build a sustainable morning routine | ...
```

The destructive control came **before the name of the thing it destroys**, on every card. Two
failures in one:

- **WCAG 2.4.3 Focus Order (A).** The sequence has to keep its meaning. A screen-reader user heard
  "Delete goal, button" with nothing yet said about *which* goal — the title arrived on the next
  stop.
- **Ordinary safety and effort.** Reaching the sixth goal meant passing through six delete
  buttons, and the owner's own words: to delete a goal you first have to identify it, so the
  order was backwards for that task too.

**Fixed 2026-09-30** in `src/components/spira/GoalCard.tsx`:

- the button now sits **last in the card** and is positioned back into the top-right corner, so
  the order is *the goal — its deadline — Start — delete*, the same shape as the resource head
  where the X closes the group rather than opening it;
- its accessible name carries the goal: `Delete "Land a backend role in Stockholm"` instead of a
  bare "Delete goal", so it is unambiguous wherever focus lands;
- a 24px spacer holds its place in the header row. Measured: the card is **542×179 before and
  after**, and the cross is in the same pixel. (With a 32px spacer the card grew 8px — the old
  button occupied 24px, being a 32px box pulled in 4px a side by a negative margin.)

Order after the fix, same walk:

```
... Calendar | Get a job as a Software Developer | Due date Nov 30, 2026 61 days left | Start
  | Delete "Get a job as a Software Developer"
```

`e2e/accessibility.spec.ts` now pins it: it tabs through the dashboard, writes down what each stop
calls itself, and fails if the delete button comes before the goal's own name. Verified red
against the old markup.

## Found by hand — 2026-09-30: the section you are in was marked by colour alone

Found with Chrome's colour-blindness emulator (Rendering — Emulate vision deficiencies). On the
goal page the sticky section nav — Goal · Reality · Resources · Options · Will do — marked the
current section **only by colour**:

```
"text-[13px] font-medium"                              <- identical on all five
active ? "text-[#F45D48]" : "text-muted-foreground"    <- the only difference
```

That is **1.4.1 Use of Color (level A)**: colour may carry meaning, never on its own.

**The obvious remedy does not work here, and the measurement is worth keeping.** Making the
active item bold changes nothing: the brand face ships one weight above Book, and the `@font-face`
for it claims `font-weight: 500 900`, so every weight from Medium up resolves to the same file.
Measured in the running app — the same string at 13px:

| font-weight | 400 | 500 | 600 | 700 |
|---|---|---|---|---|
| rendered width | 125.88px | **127.53px** | **127.53px** | **127.53px** |

So `font-medium` — `font-bold` is invisible, and would have "fixed" nothing while looking like a
fix in the diff. **On this project, a weight change is not a distinguishing signal.**

**Fixed 2026-09-30** in `src/routes/goals.$goalId.tsx`: a **2px underline** under the current item
— which is also the app's own language, since the Android GROW tab bar marks its current tab
exactly that way — and the colour moved from Guava `#F45D48` to `reserved-900 #C23928`.

One change, two criteria:

| Criterion | Before | After |
|---|---|---|
| 1.4.1 Use of Color (A) | colour only | colour **and** an underline — unmistakable under achromatopsia, where no colour survives at all |
| 1.4.3 Contrast (AA) | 3.22:1 | **5.37:1** |

Every item carries the 2px border (transparent when inactive), so marking one never moves the
words: measured, all five sit at `top=74, height=28` before and after. The axe baseline dropped
accordingly — goal workspace and map panel from 6 colour-contrast nodes to 5.

## Fix approach

Measure first, then fix — and keep it from regressing:

1. **Add automated checks** (cheap, catches the mechanical half):
   - ✅ `axe-core` via `@axe-core/playwright` on the dashboard, the goal workspace, a sheet and the
     vacancy map panel — `e2e/accessibility.spec.ts` (2026-09-29). More screens as they matter:
     the AI panel, the note editor, the filter sheet.
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
