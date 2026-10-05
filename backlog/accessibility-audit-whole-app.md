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

**`e2e/accessibility.spec.ts`** runs axe-core over three screens through the Playwright suite that
already existed — the dashboard, the goal workspace and the "New goal" sheet — against the
WCAG 2.1 A + AA rule tags. It runs in CI with every other spec
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
accordingly.

## 2026-09-30: the static layer, and what it found

**`eslint-plugin-jsx-a11y` is on** (`eslint.config.js`, the `recommended` set), so `npm run lint`
now fails on an accessibility defect it can see in the markup. It is the other half of the
automated work and covers what axe cannot reach: it reads **every** component, including screens
no E2E spec opens and states no spec produces, and it never runs the app, so it sees only what is
decidable from the JSX. axe sees the opposite — the rendered page, with real colours and real
computed names, on the surfaces a spec actually visits. Neither replaces the other.

The first run reported **56 errors** across eight rules:

| Rule | Count | What it turned out to be |
|---|---|---|
| `label-has-associated-control` | 15 | **real** — see below |
| `no-static-element-interactions` | 14 | 13 backdrops / plumbing, 1 real |
| `click-events-have-key-events` | 13 | same elements |
| `no-autofocus` | 7 | deliberate, per-line exceptions |
| `no-noninteractive-element-interactions` | 3 | editor toolbars, the pan-to-drag picture |
| `anchor-is-valid` | 2 | **real** |
| `interactive-supports-focus` | 1 | a rule limitation, made explicit |
| `heading-has-content` | 1 | false positive on a shadcn wrapper |

### The real defects, fixed

- **Fifteen labels were bound to nothing.** Every form in the app wrote `<label>Name</label>` above
  its field and never tied the two together, so a screen reader announced an unnamed text box — or,
  where the browser guessed from the placeholder, announced the field called "Optional". Now bound
  with `useId()` + `htmlFor`/`id`: the resource create/edit sheet (8), the New target form (5) and
  the note editor's Add-a-link form (2). Two of the fifteen were a label over a **group of
  buttons** (resource Type, target Type) — there is no single control to bind to, so those became a
  `<span>` with `role="group"` + `aria-labelledby` on the group, which is what actually says
  "Type" over them. One was the Note editor: a `<label>` cannot reach a contenteditable, so
  `RichTextEditor` gained an `ariaLabel` prop that lands on the editable element itself.
- **A picture could not be opened with a keyboard at all.** The thumbnail was a `<div onClick>`;
  it is a `<button>` now, named "Open <the file> full screen" rather than repeating the alt text.
- **Four overlays had no way out except the mouse.** A click on the backdrop closed them and
  nothing else did: the AI provider sheet, the end-session confirm, the composer's attach menu and
  the fullscreen picture viewer. All four close on **Escape** now (the AI content modal already
  did — its inline effect became `useCloseOnEscape`, which the other two AI overlays share).
- **The sign-in screen had two links that went nowhere** — `<a href="#">Terms</a>` and
  `<a href="#">Privacy Policy</a>`. A screen reader announced them as links and a keyboard stopped
  on them, and they did nothing at all. They are plain words until those pages exist; **writing
  them is still owed** (a legal question, not a code one).
- **`role="textbox"` with no `tabIndex`.** `contentEditable` makes an element focusable, so Tab had
  always reached it — but the role promises a tabbable control and the markup never said so.
  `tabIndex={0}` states it.

### The exceptions, and why each one is not a defect

Twenty-five findings carry a **per-line** `eslint-disable-next-line ... -- reason`, never a
file-wide or project-wide switch-off, so the same rule still bites everywhere else in the same
file:

- **a dimmed backdrop with an `onClick`** — a mouse shortcut for closing; Escape and a real button
  in the card are the keyboard's way, which is why the four missing Escapes above were fixed first
  rather than being written into the reason;
- **`onClick={(e) => e.stopPropagation()}`** — event plumbing, not an interaction;
- **the note editor's toolbars** — `onMouseDown` keeps the editor's selection alive while a button
  is pressed; every control inside them is a real `<button>`;
- **`autoFocus`** (7) — each is a field that *is* the reason the surface opened (the header search
  overlay, the attach sheet's search, an API-key field revealed by tapping Edit, the revise box,
  the link dialog's first field). Not focusing it sends the user's first keypress nowhere;
- **the fullscreen picture's drag-to-pan** — a pointer affordance; zoom and close are buttons
  beside it;
- **the inline text field** (`Inline.tsx`) — its keyboard path is **focus**, not a key: `onFocus`
  on the same `tabIndex={0}` span enters edit mode.

One rule is off for one directory: **`heading-has-content` in `src/components/ui/**`**. Those are
shadcn primitives — `AlertTitle` renders `<h5 {...props} />` and its content comes from the caller
— and a rule that reads markup cannot see through a wrapper. The call sites are linted normally,
which is where an actually empty heading would be written.

## 2026-09-30: axe now covers every surface, not three

`e2e/accessibility.spec.ts` grew from 3 screens to **every screen and overlay the web app has**,
at **two widths**:

| | Surfaces |
|---|---|
| Routes | All goals (Cards), Timeline, Calendar, Settings × its three tabs (profile / fonts / About Spira), **Sign in**, a goal workspace |
| Opened over a page | Filter & Sort panel, New goal sheet, New target sheet, Add-a-resource sheet, the deadline popover, an element's ⋯ menu, the delete confirm, a note open, a picture full screen, the AI coach |
| Phone (390×844) | All goals, the header search overlay, the Filter drawer, the New goal drawer, a goal workspace |

Three things that made it possible, and are worth keeping:

- **Sign-in needs an anonymous session.** `/login` sends an authenticated visitor home and every
  local run is authenticated (`dev@local`), so the screen was unreachable. Answering the one
  request its guard makes — `/api/auth/me` → 401 — is the whole stub.
- **The phone is a separate surface, not the same page narrower.** It draws a drawer where the
  laptop draws a side panel, and it has a search overlay the laptop has not got.
- **A recording mode.** `A11Y_RECORD=1 … --retries=0` writes each surface's counts to
  `e2e/.a11y-baseline.ndjson` instead of failing, because reading two dozen numbers out of failure
  messages one run at a time is how a baseline ends up wrong. The run it produces proves nothing
  and the numbers still have to be pasted into `ACCEPTED` and the suite re-run normally.

### 2026-10-04: each overlay is now scanned on its own, and that changed the numbers

The scan used to cover the whole page with the overlay on it, and that made every overlay's number
depend on something it does not control: a modal marks the page behind it `aria-hidden` and axe then
skips it, so whether the chrome behind was counted depended on **when** the scan ran and on what
else had been open a moment earlier. Measured: the New target sheet read 0 in one run and 6 in the
next — the whole goal page behind it — with nothing in the app changed, because the deadline popover
closing just before had restored the `aria-hidden` state the sheet had set. `scan()` now takes a
`within` selector and every overlay is measured alone.

**It made three things visible that the page behind had been masking:**

| Where | What |
|---|---|
| The ⋯ menu | its own **destructive item — "Delete option", red on white** — is the single contrast failure in it |
| The deadline card | the first node is its own **"Set deadline"**, white on Kale (the known 4.08:1 pair), then the day grid |
| The delete confirm | **3 nodes, starting with its own heading** — worth a look, since near-black on white should pass; axe may be measuring through the dim overlay |

And it removed one entry that was never the overlay's: the `aria-hidden-focus` recorded against the
⋯ menu was the page's left `<aside>` behind it, not anything in the menu. It is still a real (if
benign — focus is trapped) finding, and it is recorded here rather than in a number that implied the
wrong owner.

### What the first full sweep found

23 surfaces. **Four findings that were not the known colour pairs**, three of them fixed on the
spot:

| Surface | Rule | What it was | |
|---|---|---|---|
| Calendar | `button-name` ×2 | the month **‹** and **›** arrows are icon-only and had no name — a screen reader said "button" twice | ✅ named |
| Deadline popover | `button-name` ×1 | the **year combobox** in the calendar's caption: the value inside a `SelectTrigger` is not the control's name | ✅ `aria-label="Year"` |
| Add a resource sheet | `aria-prohibited-attr` ×1 | the `aria-label` this session put on the note editor — a bare contenteditable `<div>` is a generic element and may not carry a name | ✅ `role="textbox"` + `aria-multiline` |
| Element menu | `aria-hidden-focus` ×4 | Radix marks the page behind an open menu `aria-hidden` but leaves its links in the tab order — here the left `<aside aria-label="Main">` | recorded |

The last one is left recorded rather than patched: focus is trapped inside the menu, so nothing in
that `<aside>` is actually reachable while it is open, and the remedy is a Radix-level decision
(`modal={false}` on every menu, which also gives up the scroll lock) rather than a markup fix.

Everything else was `color-contrast`, and **the single most common node in the whole app is the
account avatar** — white initials on `bg-white/15` over the teal bar, which is the same 4.08:1 pair
as the header text and appears on every screen there is. One palette decision would take a
violation off almost every surface in the table.

**The check was verified red**, which is the only thing that makes a green one worth reading:
lowering the Calendar entry to zero fails with
`Calendar: accessibility violations beyond what is recorded in ACCEPTED. counted: {"color-contrast":2}`.

## 2026-09-30: keyboard, zoom and target size, measured without a person

Three checks that no scanner can do, because none of them is a property of one element — the tab
sequence, the layout at a width, the size of a rendered box. `e2e/a11y-helpers.ts` holds the
measuring; the specs are `a11y-keyboard`, `a11y-zoom`, `a11y-target-size`. The whole setup, what it
covers and how to run it by hand is written up in **`docs/accessibility-testing.md`**.

| Spec | Criteria |
|---|---|
| `a11y-keyboard.spec.ts` | 2.1.1 Keyboard · 2.1.2 No Keyboard Trap · 2.4.3 Focus Order · 2.4.7 Focus Visible |
| `a11y-zoom.spec.ts` | 1.4.10 Reflow (320px) · 1.4.4 Resize text (200 %) |
| `a11y-target-size.spec.ts` | 2.5.8 Target Size Minimum (**WCAG 2.2** AA, 24px — fails) · 2.5.5 (2.1 **AAA**, 44px — reported, never fails) |

### What they found

- **The goal card's "Start" link is 44×14 px** on a phone — the line of text is the whole target,
  with no padding of its own, so it misses the 24×24 floor on its short side. Eight cards, eight
  instances of one component. Recorded in `SMALLER_THAN_AA`; the card has room for it.
- **At 200 % text the goal description is cut off** — its auto-sizing `textarea` shows 79px of text
  in a 40px box and hides the rest with no scrollbar. The height is written in pixels from one
  measurement of the content and nothing measures again when the font grows, which is exactly the
  failure 1.4.4 describes. Recorded in `ALLOWED_CLIPPED`; the fix is to re-measure when the text
  metrics change.
- **Closing any overlay with Escape dropped focus on `<body>`** — and this one is systemic, not one
  screen. Radix restores focus to a `Trigger`, and **nothing in this app uses one**: every sheet and
  dialog is controlled by state and opened from an ordinary button, so Radix's own handler runs
  `event.preventDefault(); triggerRef.current?.focus()` against a ref that is `null`, the restore
  never happens, and the next Tab starts again from the top of the page. A keyboard user who opened
  the New goal sheet, changed their mind and pressed Escape was thrown back to the beginning of the
  document — on every sheet, every form and every confirm in the app.
  **Fixed** with one shared hook, `src/components/ui/restore-focus.ts`, on `sheet.tsx`,
  `dialog.tsx` and `alert-dialog.tsx`: it reads `document.activeElement` in `onOpenAutoFocus` —
  the only moment the opener is still readable from outside — and focuses it again on close if it
  is still in the document. Verified red for **both** overlay shapes: with the hook removed, the
  spec fails on the sheet *and* on the confirm.
- **Nothing uses a positive `tabindex`**, on any of the three screens checked — clean.
- **Every control on the dashboard and on a goal page is reachable with Tab, and every stop changes
  something on screen** — clean, including the reveal-on-hover controls (the option's rating badge
  and its ⋯ menu), which was the specific worry under "Steps to reproduce".

### Two measurement traps worth keeping

- **A control that replaces itself on focus looks unreachable.** The inline fields are a
  `role="textbox"` span that becomes a `<textarea>` when focus arrives, so Tab lands on an element
  the probe never tagged and the span is reported as never visited. Matching the stop by its
  accessible name is what tells the two apart from a control nobody can reach.
- **A focus ring is a difference, not a property.** Every card here has a `box-shadow`, so "the
  shadow is not none while focused" is true of controls that show nothing at all when you tab to
  them. The check re-reads the same elements with nothing focused and compares.

## The findings have their own bug

Everything the four checks measured — the contrast pairs, the tap targets, the text at 200 % — is
written up with its numbers and its pictures in
**`backlog/contrast-and-tap-targets-fail-wcag-in-named-places.md` (BUG-096)**. This file stays what
it is: the umbrella for the audit and the machinery.

## Fix approach

Measure first, then fix — and keep it from regressing:

1. **Add automated checks** (cheap, catches the mechanical half):
   - ✅ `axe-core` via `@axe-core/playwright` over **every** screen and overlay, at both widths —
     `e2e/accessibility.spec.ts` (2026-09-29, extended 2026-09-30).
   - ✅ `eslint-plugin-jsx-a11y` in the frontend lint config (2026-09-30).
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
