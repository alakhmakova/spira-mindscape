# Accessibility testing

How Spira is checked for accessibility: against which standard, what runs on its own, what a person
still has to do by hand, how to read a failure, and how to set the same thing up in another
project.

The umbrella bug is **`backlog/accessibility-audit-whole-app.md` (BUG-023)** — findings and
decisions go there; this file is how the machinery works.

---

## 1. The standard, and what "AA" actually covers

The target is **WCAG 2.1, level AA** — 50 success criteria (25 at A, 25 at AA). That is the level
public-sector and most procurement rules name, and it is what the Russian test plan in the owner's
`Документы` folder walks through criterion by criterion.

Two things are worth being precise about, because both are easy to overstate:

- **Automated rules cover roughly a third of the criteria.** A scanner can see that a button has no
  accessible name. It cannot see that the name is *wrong*, that the reading order is nonsense, that
  an error message never reaches the field it belongs to, or that a video has no captions. A green
  suite means "the machine-checkable part has not got worse", and nothing more. Treat any claim of
  "WCAG compliant because the tests pass" as false.
- **One check here is not 2.1 AA at all.** Tap-target size is **AAA** in 2.1 (2.5.5, 44×44); the
  24×24 floor only became AA in **WCAG 2.2** (2.5.8). The suite fails on the 2.2 AA floor and
  *reports* the 2.1 AAA target without failing — see `e2e/a11y-target-size.spec.ts`.

---

## 2. The four layers

| Layer | Sees | Misses | Where |
|---|---|---|---|
| **1. Lint** — `eslint-plugin-jsx-a11y` | every component, including screens no test opens and states no test produces | anything that depends on rendering: colour, size, order, what a name computes to | `eslint.config.js` |
| **2. axe** — `@axe-core/playwright` | the rendered page: computed names, roles, contrast, ARIA validity, over **37 surfaces** at two widths | anything that is a property of the *sequence* or the *layout* rather than one element | `e2e/accessibility.spec.ts`, `a11y-overlays.spec.ts` |
| **3. Browser measurements** | the tab sequence, reflow at 320px, text at 200 %, the size of every target | whether any of it is *sensible* | `e2e/a11y-keyboard.spec.ts`, `a11y-zoom.spec.ts`, `a11y-target-size.spec.ts`, helpers in `a11y-helpers.ts` |
| **4. A person** | everything the first three cannot: a screen reader, whether a name is meaningful, whether an order makes sense, whether an error is understandable | nothing — but it does not run on every commit | §6 |

Layers 1–3 run with no human in the loop. Layer 3 exists because layer 2 cannot do it: "every
control can be reached with Tab" is a property of the whole page, not of any element in it.

---

## 3. Running it

### Prerequisites

Layers 2 and 3 drive the real app, so the stack has to be up — Docker Postgres, the backend on the
`local` profile, and Vite (see `README.md` or the `run-spira` skill). Layer 1 needs nothing.

```
# 1. lint — seconds, no stack
npm run lint

# 2 + 3. the browser checks — the stack has to be running
npx playwright test e2e/accessibility.spec.ts      # axe: routes and the overlays a page opens
npx playwright test e2e/a11y-overlays.spec.ts      # axe: the overlays that need a goal with things in it
npx playwright test e2e/a11y-keyboard.spec.ts      # 2.1.1, 2.1.2, 2.4.3, 2.4.7
npx playwright test e2e/a11y-zoom.spec.ts          # 1.4.10 Reflow, 1.4.4 Resize text
npx playwright test e2e/a11y-target-size.spec.ts   # 2.5.8 AA (and 2.5.5 AAA, reported)

# or all of it, with everything else
npm run test:e2e
```

They run in CI with every other spec — the `web-e2e` job — so nothing extra is wired up.

### Reading a failure

Every assertion is written to say what to go and look at, because a red run that only says
"expected 0, got 3" is one people learn to ignore.

```
All goals: accessibility violations beyond what is recorded in ACCEPTED.
  counted: {"color-contrast":2}
  color-contrast (serious) x2
    Elements must meet minimum color contrast ratio thresholds
    first: .h-9
```

```
Goal workspace: 2 of 84 controls cannot be reached with Tab.
  button "Rate option" at 712 418
  button[menu] "Option actions" at 744 418
```

```
About Spira: text cut off by a box that cannot grow (1.4.4 Resize text).
  div.h-9 overflow-hidden → 38px of text in 36px
```

### The attachment

`a11y-target-size.spec.ts` attaches `targets-under-44px.txt` to the run: the full AAA list, which
is information rather than a failure. `npx playwright show-report` opens it.

---

## 4. How each layer works

### Layer 1 — `eslint-plugin-jsx-a11y`

The `recommended` set, on every `.ts`/`.tsx` file. It reads JSX and never runs it, so it catches
the decidable things everywhere at once: a handler on a `<div>`, an `<img>` with no `alt`, a
`<label>` bound to nothing, an invalid `aria-*`, an anchor with no destination.

Two conventions keep it honest:

- **An exception is per line, with its reason**, never a file-wide or project-wide switch-off:
  `// eslint-disable-next-line jsx-a11y/no-autofocus -- this field is the only reason the surface
  opened`. The same rule keeps biting everywhere else in the same file.
- **One rule is off for one directory** — `heading-has-content` in `src/components/ui/**`, because
  those are shadcn primitives that forward their children and the rule cannot see through a
  wrapper. The call sites are linted normally.

### Layer 2 — axe over every surface

`e2e/accessibility.spec.ts` opens each screen *and each thing that opens over a screen* — a sheet
takes focus and names its own controls, so a page that passes says nothing about the drawer on top
of it. 37 surfaces: the five routes (Settings counts three tabs), the overlays, and five again at
phone width, where the app draws a drawer where the laptop draws a side panel.

Two mechanics worth knowing:

- **Sign-in needs an anonymous session.** `/login` sends an authenticated visitor home, and in dev
  Vite *proxies* `/login` to the backend for the OAuth redirect — so a hard `goto("/login")` lands
  on Spring Security's own generated page. The spec answers `/api/auth/me` with 401 and loads `/`,
  which makes the app navigate to its own sign-in screen the way a real visitor does.
- **A test builds the state it checks.** `a11y-overlays.spec.ts` creates its own goal, option,
  Reality item, resource and two targets before opening anything, because **a check that opens
  whatever happens to be in the database measures the database, not the app**. The screenshot pass
  showed it plainly: the ⋯ menu photographed empty, because the goal the script picked had no
  options. Anything that only exists once the user has made something — a target's menu, the
  Will-do filter panel, a subtask row — is unreachable until a fixture puts it there.
- **A recording mode.** `A11Y_RECORD=1 npx playwright test e2e/accessibility.spec.ts --retries=0`
  writes every surface's counts and the first offending element to `e2e/.a11y-baseline.ndjson`
  instead of failing. Reading two dozen numbers out of failure messages one run at a time is how a
  baseline ends up wrong. **The run it produces proves nothing** — the numbers go into `ACCEPTED`
  and the suite is re-run normally.

### Layer 3 — the three measurements

All three share `e2e/a11y-helpers.ts`, which does its measuring inside one `page.evaluate` wherever
it can: a round trip per element turns a page with 120 controls into 120 round trips.

**Keyboard** (`a11y-keyboard.spec.ts`) — 2.1.1, 2.1.2, 2.4.3, 2.4.7:

1. tag every usable control with `data-a11y-probe` (`probeInteractive`) — tags *and* roles, because
   this app has `role="textbox"` spans, `role="tab"` buttons and `role="menuitem"` rows;
2. press Tab 160 times and record where focus landed (`tabThrough`);
3. the difference is what a keyboard cannot reach.

Three details that are the difference between a useful check and a noisy one:

- **Opacity is not a reason to skip a control.** Several here sit at `opacity-0` until their row is
  hovered, and whether Tab still reaches them is the question being asked.
- **Native radios are grouped.** A radio group is meant to have one tab stop; counting the others
  as unreachable would report correct behaviour as a defect.
- **A focus ring is a *difference*, not a property.** Every card in this app has a `box-shadow`, so
  "box-shadow is not none while focused" is true of controls that show nothing at all when you tab
  to them. `restingStyles` re-reads the same elements with nothing focused and compares.

**Zoom** (`a11y-zoom.spec.ts`) — the standard's two criteria, each measured directly rather than by
pressing Ctrl + (which Playwright cannot do, and does not need to):

| Criterion | What it asks | Measurement |
|---|---|---|
| 1.4.10 Reflow | content works in **320 CSS px** with no two-directional scrolling (the equivalent of 400 % zoom on a 1280px window) | a 320-wide viewport, then `documentElement.scrollWidth - clientWidth` |
| 1.4.4 Resize text | text up to **200 %** with no loss of content or function | `html { font-size: 200% }`, then no sideways scroll *and* no text cut off by a box that cannot grow |

The overflow check is the **document's own** horizontal scroll, not a hunt for wide elements: a
`position: fixed` drawer parked off-screen is wider than the viewport on purpose and scrolls
nothing. Deliberate truncation — a `line-clamp`, a one-line ellipsis — is excluded from the clipped
check, because that is a design decision at any font size.

**Target size** (`a11y-target-size.spec.ts`) — measure every control at 390px and compare against
two thresholds; inline controls inside running text are exempt, which is the standard's own
exemption and the right one here, where resource references are rendered as buttons inside a
sentence.

---

## 5. The baselines, and the one rule about them

Each of these checks has a recorded baseline, because the app had never had an accessibility pass
and failing on everything at once produces a permanently red check that everyone learns to ignore.

| Check | Baseline | Keyed by |
|---|---|---|
| axe | `ACCEPTED` | surface → rule → node count |
| keyboard | `UNREACHABLE_BY_DESIGN` | `tag[role] "name"` → reason |
| zoom | `ALLOWED_SIDEWAYS`, `ALLOWED_CLIPPED` | surface → count |
| target size | `SMALLER_THAN_AA` | `tag[role] "name"` → surface |

**The rule: every line is a defect, not a decision.** A baseline records what was true on the day it
was written so that *new* breakage still fails — a new rule, a new instance of a known rule, a
control that stops being reachable. Fix one, lower its number. Nothing is added to a baseline
without a reason written beside it, because an unexplained entry is indistinguishable from a
control nobody can use.

Some numbers in the axe baseline are **ceilings rather than measurements**, and each says so. Two
things move a count without anything changing in the app, and a baseline that ignores them produces
a suite that goes red by itself — which is the same end as one nobody reads:

- **The data on screen.** A Timeline row per goal with a deadline; a day cell per day of whatever
  month is being shown. The Calendar page is the clearest case: **2 violations in September and 4
  in October**, because a month that borrows more days from its neighbours has more muted cells.
- **The chrome behind an overlay.** A modal marks the page behind it `aria-hidden` and axe then
  skips it — but a scan that lands before the library has done the hiding counts the app header as
  well. That is a property of *when* the scan runs, not of the overlay, so every surface that opens
  over the page allows the one node the header contributes.

Every other rule on those surfaces is still held at zero, which is where a genuinely new defect
shows up.

**Verify a new check red before trusting it green.** The axe baseline was verified by lowering one
entry to zero and confirming the failure. A check that cannot fail is worse than no check, because
it reads like coverage.

---

## 6. What a person still has to do

None of this is optional; it is simply not automatable, and the two defects a person found in this
app — a delete button announced before the goal it deletes, and a navigation marked by colour alone
— were both on screens the scanner had called clean.

### A screen reader (NVDA on Windows, VoiceOver on macOS, TalkBack on Android)

Free, and the only way to hear what the app actually says.

1. Install NVDA (nvaccess.org). Start it; **NVDA menu → Tools → Speech Viewer** shows everything it
   says as text, which is far easier than listening while you work.
2. Walk one whole journey with the mouse put away: create a goal → add an option → attach a
   resource → update a target.
3. At each stop ask the three questions a screen reader answers: **what is this** (role), **what is
   it called** (name), **what state is it in** (pressed, expanded, invalid, required).

What to listen for in this app specifically: an inline field announced as editable with its label;
`{{res:…}}` references announced as links with the resource's name; the filter padlock's state;
whether a toast is announced at all.

*There is a way to automate part of this* — **Guidepup** drives NVDA from Playwright and returns
what was spoken. It is Windows-only, slow and brittle against NVDA versions, so the cheaper 90 % is
Playwright's ARIA snapshots (`toMatchAriaSnapshot`), which capture the role/name/state tree a screen
reader reads, as a file in the repository where a diff shows any change. Neither answers whether a
name is *good*.

### The rest of the manual pass

- **Keyboard, by hand.** The automated walk proves every control can be reached; a person judges
  whether the order makes sense and whether the focus indicator is visible *enough* against each
  background.
- **Zoom, by hand.** Ctrl + to 200 % and 400 % in a real browser, and the browser's own "larger
  text" setting, which is not the same thing as page zoom.
- **Colour.** Chrome DevTools → ⋮ → More tools → Rendering → **Emulate vision deficiencies**. Under
  achromatopsia, anything that relies on colour alone disappears — which is how the section
  navigation was caught.
- **Chrome DevTools → Elements → Accessibility** shows the computed name, role and the full
  accessibility tree for whatever is selected. **Lighthouse** runs axe with a nicer report but
  covers one page at a time and nothing that opens over it.
- **Accessibility Insights for Web** (free, Microsoft) adds **Assessment**: a guided manual pass
  through the criteria no tool can decide, with a record of what you answered.

---

## 7. Setting the same thing up in another project

Nothing here is Spira-specific except the selectors. In order of value for effort:

1. **Lint, 10 minutes.** `npm i -D eslint-plugin-jsx-a11y`, add `jsxA11y.flatConfigs.recommended`
   to the extends. Expect a pile of errors on an untouched codebase: triage them into *real*
   (labels, unnamed controls, dead links) and *per-line exceptions with reasons*. Do not start by
   turning rules off.
2. **axe over the rendered app, half a day.** `npm i -D @axe-core/playwright`, then one `scan()`
   helper, one `ACCEPTED` map, and a list of surfaces. Two decisions do most of the work:
   - **scan overlays as separate surfaces**, not just routes;
   - **scan at phone width as well**, if the app changes its chrome there.
   Add a recording mode from the start — you will need it the first time and every time a surface
   is added.
3. **The three measurements, a day.** `a11y-helpers.ts` in this repo is ~250 lines and depends on
   nothing but Playwright; the three specs are the usage. The parts worth copying verbatim are the
   `INTERACTIVE` selector list, the resting-vs-focused style comparison, and the radio grouping.
4. **A manual pass, scheduled.** Put the screen-reader walk on a cadence — a release, a quarter —
   and write findings as individual tickets rather than one "accessibility" epic that never closes.

Two things to decide before writing any of it, because they determine whether anyone trusts the
result:

- **What level you are claiming** (2.1 AA here) and **which checks are above it** — mark those as
  reports, not failures, or the build goes red over an aspiration.
- **What a baseline means.** If it is "known defects, shrinking", it works. If it becomes "things
  we have decided not to do", the suite stops being a measurement and nobody reads it again.
