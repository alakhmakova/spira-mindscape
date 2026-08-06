# CLAUDE.md

Guidance for Claude Code (and any AI agent) working in this repository. First-time
contributors — human or agent — should read this top-to-bottom, then `README.md` and
`specs/tech-stack.md`.

**All documentation in this repository is written in English** (`docs/`, `specs/`,
`backlog/`, CLAUDE.md, code comments) — regardless of the language used in chat.

---

## 🔒 Commit policy (hard rule)

**Claude must never create commits or push.** The user commits everything manually.

- ❌ Do not run `git commit`, `git push`, `git add` (for staging a commit), `git merge`,
  `git rebase`, `git reset --hard`, `git cherry-pick`, `git tag`, or `gh pr create` /
  `gh pr merge`.
- ✅ Read-only git is fine: `git status`, `git diff`, `git log`, `git show`, `git branch`
  (listing).
- This rule is also **enforced by a `PreToolUse` hook** in `.claude/settings.json` — the hook
  blocks committing/pushing commands even if asked. Leave staging and committing to the user.

When work is ready, summarize what changed and let the user commit.

---

## Definition-of-Done loop (required for every change)

Follow this sequence for any code change, small or large:

1. **Understand** — read the relevant code and the docs in `docs/` / `specs/` *before* editing.
   Reuse existing functions and patterns; don't reinvent.
2. **Change small** — make focused edits that match the surrounding code's style and idioms.
3. **Self-review** — run `/code-review` on the diff (a fresh-subagent review of the current
   branch/diff). `/code-review ultra` is the deeper cloud multi-agent variant — it is
   **billed and user-triggered**, so don't launch it yourself.
4. **Verify** — run the fast checks and fix any failure:
   - Frontend: `npm run lint`, `npx tsc --noEmit`, `npm test`
   - Backend (if touched): `cd backend && .\mvnw.cmd test`
   - The `Stop` hooks also run lint/typecheck + fast unit tests and will surface failures.
5. **Cover** — add the right test levels for new behavior:
   - Web: Vitest (unit) + backend JUnit/GraphQL integration + Python E2E; **Playwright** for
     web E2E.
   - Android: JUnit/Kotlin (unit) + **Compose UI Test** (components) + **Maestro** (E2E on
     emulator).
   - **Maestro reminder (deferred — act on this):** Android E2E via Maestro only pays off once the
     app has real **multi-screen user journeys** (e.g. sign-in → dashboard → open a goal → update a
     target → see progress). Until then, sign-in is verified manually and unit/Compose tests
     suffice. **When such flows actually land** (native-mobile Steps 4–6), the agent should
     **proactively remind the user** to install Maestro and add flows, and offer to write them —
     see `docs/maestro-e2e-guide.md`. Don't push Maestro before there are cross-screen journeys
     worth testing.
6. **Security** — first ask whether the change even has a **security surface**: does it touch
   auth or sessions, another user's data, untrusted input (user text, files, URLs, tokens),
   external calls, secrets, a new endpoint/permission, or client-side credential storage?
   - **If no** (styling, copy, a pure refactor, a docs edit): skip this step — do **not** add
     security theater.
   - **If yes**, then:
     - **Reuse the existing model; don't reinvent it.** Follow `docs/security-model.md` and
       `specs/2026-06-12-security-hardening/`: per-user owner-scoping (`findByIdAndUserId`),
       server-side validation, CSRF on mutations, secrets only in env / Secret Manager (never in
       code, logs, or committed files), least privilege, and never trusting client-supplied data.
     - **Implement the safe option**, and **add a test for the boundary the change creates** —
       e.g. cross-user isolation, auth-required (401), CSRF-required (403), invalid input
       rejected, unverified data refused, a credential kept out of backups. Examples already in
       the repo: `CrossUserIsolationIntegrationTest`, `SecurityIntegrationTest`,
       `MobileAuthControllerTest` (session-fixation + token audience/verification).
     - **Surface real risks to the user** and record them in `backlog/`, rather than shipping a
       known hole silently.
   When unsure whether something is a genuine risk, **ask** — proportionality over paranoia.
7. **Document** — for a **big** step (new module, new auth/deploy path, architectural "why"),
   propose an entry in `docs/` or `specs/` and ask the user. Skip docs for small edits
   (renames, styling, bugfixes) — code, git history, and tests cover those.
8. **Hand off — don't commit.** The user commits.

---

## UI conventions (hard rules — web *and* Android)

These are **non-negotiable** and apply to every surface (React web, native Compose).

### 1. Never ship raw, un-customized default elements

**Every UI element must be a Spira-designed, themed component that you build.** Never use a bare,
un-styled platform default:

- **Web:** don't drop raw `<input>`, `<select>`, `<textarea>`, `<button>`, native checkbox/radio,
  or an un-styled third-party widget straight into product UI. Use (or extend) the design-system
  components in `src/components/ui/` and `src/components/spira/`. If a needed primitive doesn't
  exist yet, **build it** with the design tokens (`src/styles.css`) — don't inline an unthemed tag.
- **Android:** don't use naked Material 3 defaults (`OutlinedTextField` with a floating label for
  inline text, default `Button`/`Card`/progress, un-themed dialogs) where a Spira component
  belongs. Use the kit in `android/app/src/main/java/com/spiramindscape/android/ui/components/`
  (`SpiraCard`, `SpiraSection`, `SpiraButton`, `InlineEditText`, `ConfidenceStepper`,
  `DeadlineField`, `SpiraFormSheet`, `ConfirmDialog`, `CircularProgress`, …). If a primitive is
  missing, **add it to the kit**, themed via `ui/theme/` — never scatter one-off raw widgets.

The two surfaces mirror one design (see `specs/tech-stack.md` "Styling strategy" and
`specs/2026-07-16-mobile-design-and-parity/`): teal primary, the shared tokens, Playfair Display
headings. A raw default element breaks that coherence and is a review-blocking defect.

### 2. Inline inputs (the goal-page editing pattern)

`specs/tech-stack.md` → "Goal Page" mandates **inline editing**. Inline text fields (goal
title/description, target titles, reality items, options, checklist tasks, numeric values) must
behave like the web `InlineText`/`AutoTextarea` and the Android `InlineEditText`:

- **Look like the surrounding text** — no box, no floating label, no form-input chrome. Show a
  muted **placeholder** when empty, not a label.
- **Commit on blur (focus loss) and on the Done/Enter key** — never write on every keystroke.
- **Escape reverts** (web) to the last committed value.
- **Required fields never save empty** — revert to the last good value if the user clears them
  (e.g. goal title, target title, option/reality text).
- **Re-seed from the source value** when it changes externally (after a refetch/optimistic update).

Boxed, labelled inputs (`SpiraTextField` on Android, the web `Input`) are only for **create/edit
forms in sheets/drawers**, not for inline editing on the goal page.

### 3. Icons & emoji

Per `specs/2026-06-07-ai-assistant-cards-and-drawers/requirements.md` and the icon convention in
`specs/tech-stack.md`:

- **No emoji anywhere** — not in UI text, notifications, empty states, badges, or AI/assistant
  replies. (Use a word like "Achieved", not `✓`/`🎉`.)
- **Use Lucide icons only.** Web: `lucide-react`. Android: the Lucide glyphs in
  `ui/icons/SpiraIcons.kt` (built from Lucide SVG **path data** — the "PATHS + Ic" approach the
  spec names). Do **not** use Material Icons (`androidx.compose.material.icons.*`) or ad-hoc
  drawn shapes. If an icon is missing, add its Lucide path to `SpiraIcons` (copy the `d`
  attribute from https://lucide.dev) — keep the two surfaces on the same icon set.

### 4. Verify UI changes visually before shipping

Existence-only assertions lie: a drawer once rendered with half its content pushed off-screen
while `assertExists` stayed green. **Any visible UI change must be verified by looking at
pixels** before distributing: render the changed surface in one of the
`android/app/src/test/java/com/spiramindscape/android/ui/VisualCheck*Test.kt` classes (each writes
PNGs to `app/build/reports/visual/`) and open the image, or screenshot the emulator (`adb exec-out
screencap`). Never claim a visual fix without having seen it.

> ⚠️ **The `VisualCheck*` suite is SLOW and currently has a HANGING test — do not run the whole
> suite blindly.** Each class re-inits Robolectric NATIVE graphics under `forkEvery = 1`, so a full
> `:app:testDebugUnitTest` sweep takes **~15 minutes**, and
> `VisualCheckRealityDraftSaveTest` **hangs indefinitely** (`performTextInput` inside a
> `ModalBottomSheet` never idles — the unresolved side of **BUG-009**, see
> `backlog/android-visual-test-suite-flaky-appnotidle.md`). That test is `@Ignore`d so it doesn't
> wedge the run, but **until BUG-009 is fully fixed, do not add new tests that type into a
> `ModalBottomSheet`, and prefer running a single `--tests "...VisualCheck<one>Test"` class** (or
> just render its PNG) instead of the whole suite. If a run appears stuck in
> `> Task :app:testDebugUnitTest`, stop it with `cd android && ./gradlew.bat --stop`.

### 5. Menus & overlays are pure white

**All dropdowns, menus, popovers, and overlay surfaces have a plain white background** — no
tint. On Android this means clearing Material's tonal-elevation overlay (`surfaceTint =
Color.Transparent` in the theme) so menus don't pick up a teal cast; on the web, don't let a
popover inherit a tinted/elevated background. If a menu looks greenish/grey, it's wrong — fix the
surface, don't ship it.

### 6. Dropdown / menu anatomy (hard spec — don't reinvent)

There is **exactly one** menu surface on Android: `ui/components/SpiraDropdownMenu.kt`
(`SpiraDropdownMenu` + `SpiraMenuItem` + `SpiraMenuDivider`). **Never** use Material's
`DropdownMenu` / `DropdownMenuItem` in product UI, and never hand-roll a one-off menu — Material's
default reads as a flat grey rectangle and was explicitly rejected. Every sort/filter menu, kebab
(⋮) menu, and action menu uses `SpiraDropdownMenu`. If it can't express what you need, **extend
that file**, don't fork it.

The look it must always produce (from the reference the owner supplied — a clean floating card
menu):

- **Pure white** background (`SpiraSurfaceRaised`), never tinted or elevation-grey.
- **Width fits its content** (`IntrinsicSize.Max`) with a sensible `min` — it never stretches to
  the full screen width.
- **Generously rounded** corners (**20dp**), a **1dp hairline border** (`SpiraBorder`), and a soft
  **shadow** (`shadowElevation ≈ 12dp`) so it floats as a card, not a box.
- Each row (`SpiraMenuItem`) = **label on the left**, an **icon in a right-aligned column** that
  lines up across every row (label cell flexes with `weight(1f)`; the icon slot is fixed width so
  even icon-less rows keep the column aligned). Comfortable padding (~20dp horizontal, ~13dp
  vertical).
- **Destructive** items (Delete) are red (`colorScheme.error`); a **selected** item in a pick-one
  menu shows a check in the icon slot.
- Anchored just below its trigger, right-edge aligned, flipping above near the screen bottom;
  dismiss on outside-tap / back.

If a menu doesn't look like that floating white card, it's wrong — fix `SpiraDropdownMenu`, don't
ship a different-looking menu.

---

## Brand design system (hard rules)

These are the Spira brand rules — typography, colour, and the usage "do / avoid" list. They apply
to **every** surface (web + Android). The Android tokens live in
`android/app/src/main/java/.../ui/theme/` (`Color.kt`, `Type.kt`) — change the token, not one-off
values.

### Typography

These are the **actual fonts we use** (loaded via Google Fonts on both web and Android):

| Role | Font (brand → loaded fallback) | Leading | Tracking |
|---|---|---|---|
| Headline | **ITC Clearface** (serif) → **Playfair Display** → Georgia | 110% | tight |
| Body | **GCentra** (sans, Book 400 + Medium 500) → system sans | 130% | 0 |

The brand faces are **ITC Clearface** (headlines) and **GCentra** (body) — Gusto's **licensed**
brand fonts, now **present** in `public/fonts/` (web) and `res/font/` (Android), so they render on
both surfaces. **Playfair Display** stays as the serif fallback; **Roboto has been removed** —
GCentra is the sole sans. GCentra only ships Book (400) + Medium (500), and Medium covers every
heavier weight, so **bold/semibold text renders GCentra Medium** (no Roboto, no faux-bold).

**To activate the brand fonts:** drop the licensed files into **`public/fonts/`** (web — see
`public/fonts/README.md`) and **`res/font/`** (Android). Full steps for any font swap live in
**`docs/changing-fonts.md`**.

- **Leading:** headline line-height = 110% of size; body = 130%. (Applied in `Type.kt` via
  `lineHeight`.)
- **Tracking:** headlines use a slightly tighter negative `letterSpacing` so serif glyphs are
  optically balanced but **never touch**; body tracking is 0.
- **Alignment:** left or center — whichever suits. Always leave **clear space between the header
  and body** so the hierarchy reads.
- **Line length:** headlines are **3–5 words per line, max** — keep them short for impact. If it's
  long-form, it's body copy → set it in the body font, not the headline serif.
- Headings use the serif; everything else uses the sans. If a heading font is ever swapped, change
  **only** `HeadingSerif` in `Type.kt` — the leading/tracking/weight rules above stay.

### Font loading strategy

**Web** (`src/styles.css`): `@font-face` blocks declare **GCentra** (Book 400 + a Medium face that
claims `font-weight: 500 900`) and **ITC Clearface**, pointing at `public/fonts/…` (served at
`/fonts/…`); the `--font-heading` / `--font-sans` / `--font-display` tokens list the brand font first,
then system fallbacks (no Roboto). The Google Fonts `@import` (mirrored by the `<link>` in
`index.html`) loads only **Playfair Display** + JetBrains Mono now.

**Android** (`Type.kt`): bundles **ITC Clearface** (headlines) and **GCentra** (body — Book + Medium,
with Medium also registered at `FontWeight.Bold`) under `res/font/`; `HeadingSerif` = ITC Clearface,
`BodySans` = GCentra (no Roboto). To swap either, bundle the replacement under `res/font/` and point
`HeadingSerif` / `BodySans` at them (see `docs/changing-fonts.md`).

Full step-by-step swap instructions (both surfaces) live in `docs/changing-fonts.md`.

### Colour

Two brand colours, exact hexes (full tint ramps are in `Color.kt`):

- **Guava** (coral) `#F45D48` — the brand **accent/highlight**. Logo colour.
- **Kale** (teal) `#0A8080` — used for **UI surfaces, buttons, active states, teal bands**.

> Nuance: the guidelines name Guava the "primary brand colour," but they also forbid Guava as a
> background, and both our app and the reference product UI are teal-forward. So in product UI
> **Kale is the working primary (fills/buttons/bands) and Guava is the accent only.**

Supporting neutrals (backgrounds & greys): **Ginger** (warm), **Parsnip** (warm-grey),
**Salt** (neutral grey ramp `Salt-200…Salt-1000`), and **White**.

**Full brand palette — exact hexes. Together with the extended ramps that follow, these are the
ONLY colours allowed in product UI. Do not invent intermediate shades; if you need a colour, pick
one from either table.**

| Ramp | Values (light → dark) |
|---|---|
| **Guava** (coral accent) | `100 #FFF3EF` · `200 #FEEFE8` · `300 #FAC6B9` · `400 #F49582` · **`500 #F45D48`** · `600 #EF523C` |
| **Kale** (teal — working primary) | `100 #F3FAFB` · `200 #E0F2F5` · `300 #8DD3D4` · `400 #2BABAD` · **`500 #0A8080`** · `600 #005961` |
| **Ginger** (warm background) | `100 #FFFAF2` · `200 #FFF2DF` |
| **Parsnip** (warm-grey background) | `100 #FBFAFA` · `200 #F8F5F2` |
| **Salt** (neutral grey) | `200 #FBFAFA` · `300 #F4F4F3` · `400 #EAEAEA` · `500 #DCDCDC` · `600 #BABABC` · `700 #919197` · `800 #6C6C72` · `900 #525257` · `1000 #222525` |
| **White** | `#FFFFFF` |

Typography colour is **Salt-1000 `#222525`** on light. Ginger/Parsnip have only the two light
tints shown (there is no darker Ginger/Parsnip — for a stronger tone use Salt or Kale).

#### Extended ramps (also allowed)

The full-resolution ramps below are **equally allowed** in product UI and are the ones to reach for
when a shade in the short palette above is too coarse — a hover state between two steps, a semantic
state (success / error / warning / info), or a long neutral scale for text and dividers. They do
**not** replace the palette above: both lists are valid, and everything already shipped in
`Color.kt` / `styles.css` stays as it is.

The two brand colours are the same colours in both lists — **`brand-800 #0A8080` is Kale-500** (the
working primary) and **`reserved-600 #F45D48` is Guava-500** (the accent). `reserved` is the Guava
family; it keeps its name from the brand source, and the **"never as a large fill" rule applies to
every step of it**, not just to `600`.

| Ramp | Values (light → dark) |
|---|---|
| **neutral** | `0 #FFFFFF` · `100 #FAFAFA` · `150 #F6F6F6` · `200 #F3F3F3` · `300 #E5E5E5` · `400 #D6D6D6` · `500 #C8C8C8` · `600 #BABABA` · `700 #ABABAB` · `800 #9F9F9F` · `900 #929292` · `1000 #858585` · `1100 #787878` · `1200 #6B6B6B` · `1300 #5F5F5F` · `1400 #535353` · `1500 #474746` · `1600 #3C3C3C` · `1700 #313131` · `1800 #262626` · `1900 #1C1C1C` |
| **brand** (teal — working primary) | `100 #F9FDFC` · `150 #F0FCFB` · `200 #E5F4F3` · `300 #CCE8E7` · `400 #7EC5C4` · `500 #4CACAC` · `600 #3D9F9E` · `700 #2C9191` · **`800 #0A8080`** · `900 #007777` · `1000 #005C5C` · `1100 #003737` · `1200 #182928` |
| **success** | `100 #F8FDF7` · `150 #ECFAEE` · `200 #E0F6E5` · `300 #BCEECE` · `400 #5FCD91` · `500 #40B178` · `600 #30A46C` · `700 #1B9660` · `800 #008954` · `900 #007A4B` · `1000 #005F39` · `1100 #003920` · `1200 #1C2920` |
| **reserved** (coral accent = Guava) | `100 #FFFAF8` · `150 #FFF4F1` · `200 #FFEDEA` · `300 #FFDAD4` · `400 #FFA090` · `500 #FF725D` · **`600 #F45D48`** · `700 #E4523E` · `800 #D34533` · `900 #C23928` · `1000 #9F2013` · `1100 #680200` · `1200 #34211D` |
| **error** | `100 #FFFBFB` · `150 #FFF4F3` · `200 #FFEDEB` · `300 #FFDAD7` · `400 #FF9F99` · `500 #FF716C` · `600 #FA5958` · `700 #E84D4C` · `800 #D74041` · `900 #C53336` · `1000 #A31821` · `1100 #68000C` · `1200 #34211F` |
| **warning** | `100 #FFFBF7` · `150 #FEF5EC` · `200 #FFF2DF` · `300 #FFDEA1` · `400 #EBAF00` · `500 #C99500` · `600 #B98900` · `700 #A97D00` · `800 #997000` · `900 #896500` · `1000 #6B4E00` · `1100 #402D00` · `1200 #2D2416` |
| **info** | `100 #FDFCFF` · `150 #F4F7FF` · `200 #EBF1FF` · `300 #D4E3FF` · `400 #8ABBFF` · `500 #56A0F9` · `600 #4793EA` · `700 #3686DC` · `800 #2279CE` · `900 #006CC1` · `1000 #005397` · `1100 #00315D` · `1200 #1E2633` |
| **intelligence** (AI surfaces) | `100 #FEFBFF` · `150 #F9F5FE` · `200 #F4EEFD` · `300 #E6DFF9` · `400 #BDAEFF` · `500 #A28DFF` · `600 #957EF9` · `700 #8871EB` · `800 #7B63DD` · `900 #6E56CF` · `1000 #563CB5` · `1100 #320A93` · `1200 #272431` |

Reading the steps: **100–300** are tints for backgrounds and hairlines, **400–700** are mid-tones
for borders, icons and muted text, **800–1000** are the solid/on-white-text steps, and
**1100–1200** are the deep shades. `success` / `error` / `warning` / `info` are **semantic** — use
them for state (a saved confirmation, a validation error, an overdue warning, an informational
note), not as decoration. `intelligence` is the assistant/AI accent.

Everything in the "Colour rules" list below still applies to these ramps too — white stays the
canvas, tints stay sparse, and `reserved` (Guava) is never a large fill.

**Colour rules — do NOT break (these are the guidelines' "avoid" list):**

- **White is the primary canvas.** Use it more than any colour; let colour bring the white space
  to life. **Tints are used sparingly.**
- **NEVER use Guava as a large background/fill colour** (page/section/card backgrounds) — it's an
  accent. Small accent **marks** in Guava are fine (e.g. the Options "active" corner-check ribbon).
- **NEVER use white copy on a light background colour.**
- **NEVER use black / `#222525` copy on Kale** — text on teal is white/light.
- **NEVER use Guava as a text colour over Kale.**
- Don't mix colours in ways that hurt legibility; keep combinations from the approved pairs.

The **active-option corner check** ribbon is **Guava** (`tertiary`) with a white check — a small
accent mark, not a fill (an allowed accent use of Guava).

Semantic mapping already wired in `Color.kt` → `Theme.kt`: primary = Kale-500, accent/tertiary =
Guava-500 (accent marks only, no large fills), foreground = Salt-1000, muted = Salt-800, border =
Salt-500, background = Parsnip-100, Options page = Kale-500 (teal), cards/menus = White, destructive =
Guava-600. (`success` is a functional green — new work should take it from the **success** ramp
above rather than picking a fresh green.)

> The type params above (**leading / tracking / alignment**) are **font-independent** — they're set
> on the type scale (`Type.kt`) and per-usage alignment, so they hold no matter which heading font
> ships. Don't tie them to a specific font.

### Goal-workspace screen header (every tab)

Every goal-workspace tab opens with the shared **`GoalTabIntro`** block (`GoalWorkspaceScreen.kt`):

- a **left-aligned kicker label** = the screen/phase name in the brand label style (`labelLarge`,
  bold, teal — or white on the teal Options tab), then
- a **centered heading + centered description** below it, with **clear space between the kicker and
  the heading** (the brand "clear space between header and body" rule).

Use it on **all five tabs** (Goal, Reality, Resources, Options, and the Targets tab). The five
kicker labels are `GOAL / REALITY / RESOURCES / OPTIONS / WILL DO` — note the fifth tab's bottom-nav
label stays **"Targets"** but its on-screen kicker reads **"Will do"** (the GROW "Will" step). Don't
re-center the kicker and don't drop the header/body spacing.

### Options cards (interaction)

- The Options screen background is the teal **primary** (Kale-500); cards are white and pop off it.
- Card **title + strategy text are centered**; the "Option N" number is **display-only** and
  renumbers automatically.
- **Reorder is drag-and-drop**: long-press a card and drag it into place (`OptionCard`'s
  `detectDragGesturesAfterLongPress` → `onReorderOption`). There is **no** reorder-by-editing-the-
  number.
- The card menu is a **bottom-centre "bump"** — a small white **half-oval** tab with a **horizontal**
  ⋯ (`OptionMenuBump`, shape `HalfOvalDown`) hanging **entirely below** the card's bottom edge (it
  must not overlap the white card). Tapping it **flips the card into a menu in place**
  (`OptionCardMenu`: close button + Make active/Deactivate + Delete) —
  **not** a dropdown and **not** a kebab. The close (✕) button leaves menu mode.
- The **active-option** marker is a **Guava** corner-check ribbon (top-right); no "Active" label.

For implementation details and testing, see `docs/drag-and-drop-options.md`.

---

## Claude Design (claude.ai/design)

Claude Design (`https://claude.ai/design`) is the visual design tool: designers or PMs create screens
there, and you import them into this codebase to implement.

**How to import a design:**

1. **Open the design file** in Claude Design (you or a teammate shares a link like
   `https://claude.ai/design/p/<projectId>?file=<filename>.html`)
2. **Authorize MCP access** (one-time): run `/design-login` in Claude Code to authenticate to claude.ai/design
3. **Use the claude_design MCP** (once authorized):
   - The MCP endpoint `https://api.anthropic.com/v1/design/mcp` is already available
   - You can read design files, component specs, and preview assets from the shared design project
   - Reference the design's file structure to understand layout, component naming, and interaction patterns
4. **Implement in the codebase**: translate the design specs into React (web) or Compose (Android) code
   - Use Spira's design components (`src/components/spira/`, `ui/components/` on Android)
   - Apply brand tokens (colours, typography, spacing — see `src/styles.css` and `Type.kt`)
   - Match the reference screenshots for visual fidelity (see CLAUDE.md rule #4: verify UI changes visually)

**When to use**:
- A designer creates a screen mockup and shares a link
- You need to see the exact layout, spacing, interactions, or component composition
- You want to verify your implementation matches the reference design

**What NOT to use**: do not use `/design-sync` to upload the codebase as a design system (unless you're
building a component library for designers to use). That's a separate workflow for design-system repos.

---

## Bug backlog (`backlog/`)

`backlog/` is the project's bug tracker — **one Markdown file per bug**. See
`backlog/README.md` for the format.

- **Read `backlog/` when starting work**, and **remind the user about open bugs** so the
  accumulated backlog actually gets fixed over time.
- It is fed mainly by the **user proposing bugs**. Findings from `/code-review` do **not** go
  here — those are fixed within the same change, not tracked as standing bugs.
- Each bug file states, in English: a clear descriptive filename, a `Status`
  (`🐞 Open` / `🔧 In progress` / `✅ Fixed`) that makes "fixed or not" **unambiguous**,
  a summary, **steps to reproduce**, **root cause**, **fix approach**, **how to verify
  fixed**, and a **Resolution** filled in when done.
- When a bug is fixed, flip its `Status` to `✅ Fixed` and complete the Resolution.

---

## Build / run reference

| Task | Command | Notes |
|---|---|---|
| Frontend dev | `npm run dev` | Vite on `http://localhost:5173` |
| Frontend tests | `npm test` | Vitest |
| Frontend build | `npm run build` | |
| Backend run | `cd backend && .\mvnw.cmd spring-boot:run` | **`mvn` is NOT installed — always use `.\mvnw.cmd` (Windows) / `./mvnw` (bash)** |
| Backend run (no Google login) | `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` | Auto-logs-in `dev@local` |
| Backend tests | `cd backend && .\mvnw.cmd test` | |
| Android build | `cd android && .\gradlew.bat :app:assembleDebug` | Emulator reaches local backend at `http://10.0.2.2:8080` |
| Android distribute (APK → email link) | `cd android && .\gradlew.bat distributeDebug -PreleaseNotes="what changed"` | Builds the debug APK and uploads it to Firebase App Distribution; testers (incl. the owner) get an email link. Uses your `firebase login`. |

Full local run (DB + backend + frontend), ngrok mobile testing, and deploy details are in
`README.md`.

### 📦 Always distribute the APK after Android app changes (hard rule)

**After completing any change that affects the Android app (UI, behavior, dependencies — anything
that changes what runs on the device), always build and distribute the APK** so the owner gets a
fresh email link to test on a real phone:

```
cd android && .\gradlew.bat distributeDebug -PreleaseNotes="<short summary of what changed>"
```

- This runs `assembleDebug` and uploads to **Firebase App Distribution**, which emails the testers
  (the owner's address is the default) a download link.
- Always pass `-PreleaseNotes="…"` describing the change so the email is meaningful.
- Do this as the final step of the Definition-of-Done loop for Android work, **without waiting to
  be asked** — the owner tests on-device from that email link.
- It relies on the Firebase CLI being logged in (`firebase login`). If the upload fails with an
  auth error, that login is interactive and **only the user can do it** — surface the exact command
  and ask them to run it (e.g. `! firebase login`), then retry `distributeDebug`.
- Skip only for changes that cannot affect the running app (pure docs/backlog edits, web-only or
  backend-only work).

---

## Diagnosing the app (agent self-service vs. user)

**The agent can and should do these itself** before asking the user: build (frontend/backend/
Android), run unit tests, run the app, and — when a **runtime** error is suspected — launch it
on an **emulator** and read **`adb logcat`** to reproduce and inspect the error. Do this rather
than relying on the user to relay logs.

**Only the user can do:** complete an interactive **Google sign-in** (a real account + consent
in the system UI), and any action in the **Google Cloud / Firebase web consoles** (creating
OAuth clients, Firebase projects, secrets). The agent has no browser access to those and cannot
tap on a physical device.

## Where things live

- **Frontend** (`src/`): routes in `src/routes/`, product components in `src/components/spira/`,
  UI primitives in `src/components/ui/`, domain logic in `src/lib/spira/` (`types.ts`,
  `progress.ts`, `store.ts` = Zustand state + optimistic sync, `api.ts` = GraphQL client,
  `auth.ts` = auth store + CSRF).
- **Backend** (`backend/src/main/java/com/spiramindscape/backend/`): `graphql/` controller,
  domain packages (`goal/`, `target/`, `resource/`), `auth/` (Google OAuth + users),
  `config/SecurityConfig.java`, `ai/` (AI + GROW/RAG). Schema:
  `backend/src/main/resources/graphql/schema.graphqls`. Migrations:
  `backend/src/main/resources/db/migration/`.
- **Android** (`android/`): native Kotlin/Jetpack Compose app (Apollo Kotlin GraphQL client).
- **Docs**: `docs/` (guides), `specs/` (mission, roadmap, tech-stack, dated feature specs).

## Architecture in one paragraph

Single-origin web app: a React SPA served by the same Spring Boot container that exposes a
**GraphQL** API (`/graphql`), backed by PostgreSQL. Auth is **Google Sign-In only** (OAuth2/OIDC)
with **server-side sessions in PostgreSQL** (`spring_session`) — so data is centralized and
per-user across every surface (desktop web, responsive mobile web, and the native Android app,
which reuses the same API). See `specs/tech-stack.md` for product/technical direction and
`specs/roadmap.md` for phased plans (native mobile is Phase 13).
