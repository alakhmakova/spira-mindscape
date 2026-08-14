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
   - **Writing a `catch` block, or tempted to add a log line?** See "Logging" below.
7. **Document** — for a **big** step (new module, new auth/deploy path, architectural "why"),
   propose an entry in `docs/` or `specs/` and ask the user. Skip docs for small edits
   (renames, styling, bugfixes) — code, git history, and tests cover those.
8. **Hand off — don't commit.** The user commits.

---

## Logging (hard rules)

Full reference: **`docs/logging.md`**. This section is the part you need while writing code.

### 1. The default is: add nothing

Most new code needs **zero** log statements, because failures that are already loud are already
captured:

| You write | What is logged for you |
|---|---|
| A new GraphQL resolver or REST endpoint | Any unexpected exception → `ERROR` with the stack trace, `traceId` and `userId`; the client gets a sanitized `Reference:` (`GraphQlExceptionHandler`, `RestExceptionHandler`) |
| A new React component | A throw during render → `ErrorBoundary` → reported to the backend |
| Any un-awaited promise on the web | `unhandledrejection` → reported |
| A new optimistic write through the store | `setSyncError` is the shared funnel and already reports |
| A new Android screen | A crash → Firebase Crashlytics, automatically |

So do **not** sprinkle "entering method X" / "saved successfully" lines. They are noise, they cost
log volume, and Cloud Run already writes an `httpRequest` entry for every request.

### 2. When a log line IS required

> **Log when a failure is invisible to the user AND something is lost.**

In practice that means a `catch` (or `runCatching`, or `.catch()`) after which the app pretends
nothing happened. Ask two questions:

1. **Will the user notice?** If they get an error state, a banner or a toast — no log needed.
2. **Was anything lost?** A save that didn't persist, a request that never reached the server, a
   transcript silently not stored. If nothing was lost — no log needed.

Both "yes" is exactly the defect class that made Android's delete-goal do nothing at all
(BUG-034): confirmed dialog, no navigation, no message, no trace anywhere.

**And if the user genuinely can't tell something failed, a log is not enough — surface it in the
UI too.** A log tells *us*; it does not fix the dead-button feeling. Use `SpiraInlineBanner`
(Android) or the existing sync-error banner (web).

**Counter-example — do NOT log:** `ui/util/DateFormatting.kt` swallows six parse failures on
purpose. A malformed date is expected input, not a defect; logging it would be pure noise.

### 3. How to write it

```java
// Backend — throwable LAST, with no {} placeholder. Never e.getMessage().
log.warn("goal_save_failed goalId={}", goalId, e);
```
```ts
// Web — reportError reaches us; warn/debug/info stay in the developer's console.
logger.reportError(err, { kind: "api" });
logger.warn("clipboard fallback used", err);
```
```kotlin
// Android — one call writes to logcat AND records a Crashlytics non-fatal.
SpiraLog.w(TAG, "goal_save_failed goalId=$goalId", e)
```

Message format is `snake_case_event key=value key=value`. `reason=` values must be **fixed codes**
(`token_invalid`, `account_conflict`), never a string built from user input.

Levels: **ERROR** = we must fix it (alertable) · **WARN** = expected but notable (rejected auth,
rate-limit block, a client error report) · **INFO** = lifecycle facts, a handful per request at
most · **DEBUG** = off in production.

### 4. Never log (enforced by a test)

Secrets and tokens (API keys, Google ID/refresh tokens, session ids, CSRF tokens) — and **the
user's own text**: goal/reality/option/target content, resource notes, AI prompts and completions,
uploaded file contents, email addresses (use the numeric `userId`).

**Log a measurement or a shape instead of the content** — `chunks.size()`, `data.length()`, or the
JSON field names. This is not hypothetical: `GeminiProvider` shipped a raw model chunk at **WARN**,
so real users' goal text went to Cloud Logging in production.

`LoggingConventionTest` scans `src/main/java` and **fails the build** on a violation. If it fires
and you believe the line is safe, the cause is almost always a **misleading variable name**
(`body`, `chunk`, `payload`) — rename it, or sharpen the heuristic. Adding an entry to its
`ACCEPTED` map is the last resort, and it must carry its reasoning.

### 5. Don't reinvent the plumbing

`traceId`/`userId` are already in the MDC for the whole request and become top-level JSON fields —
never pass them as arguments. Two documented gaps: MDC does **not** reach the AI chat's SSE worker
threads, and it would be lost by an async (`CompletableFuture`/`Mono`) data fetcher if one is ever
introduced.

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
- **Android is on Gravity UI** (MIT, (c) 2022 YANDEX LLC) — **every glyph**, in
  `ui/icons/SpiraIcons.kt`, ported by the owner's decision on 2026-08-14. Gravity was measured
  against five other collections in **`specs/icon-sets.md`** and was the only exact match for the
  drawing system Spira's look was modelled on: a **16 box**, a **1.5 wall**, `.75` radii, the
  `1.06` diagonal, and the line drawn as an even-odd **fill** rather than a stroke.
  - There is exactly **one builder**, `gravity()`. The Iconoir, Phosphor and hand-drawn builders
    are gone, and with them the ink-weight corrections they needed — one set cannot disagree with
    itself, which is what `PHOSPHOR_INK_BOOST` existed to paper over.
  - **Take the glyph Gravity already has.** If it has nothing that suits, **ask the owner** rather
    than drawing one or reaching into another set. Six marks had no Gravity equivalent (a brain, a
    marker, a calendar-plus, a leaf) and the owner chose the substitutes.
  - **One glyph, one name, and the name must describe the glyph.** No `Nav` prefix: it used to
    mean "the 16-grid twin of a 24-grid icon" and means nothing now that there is one set. A name
    that survives its glyph is a defect — `NavTrophy` drew a bar chart, `SlidersHorizontal` drew
    aligned bars, `ChevronDownSolid` drew a caret. Rename with the glyph, or don't change it.
  - **Web is still on Lucide** (`lucide-react`), plus the glyphs in
    `src/components/spira/brand-icons.tsx`. The two surfaces are deliberately apart until the web
    is ported too; a glyph the owner supplies goes to **both**.
- **Never put a solid mark in a column of outline ones.** Gravity's `-fill` twins exist for exactly
  one purpose: marking the **selected** footer item next to its outline sibling
  (`NavResources` / `NavResourcesFilled`). The drawer's trophy was once a filled cup beside five
  hollow glyphs and it was the loudest thing on the sheet.
- Do **not** use Material Icons (`androidx.compose.material.icons.*`) or ad-hoc drawn shapes, and
  **no emoji as icons**. **No hollow dots**: an icon whose eyes are drawn as tiny rings reads at
  16dp as a rendering artefact — Gravity's `face-smile` / `face-sad` draw theirs solid, which is
  why they were acceptable as the Options card's rating badge.
- **Porting a glyph to Android** — take the `d` of each `<path>` from
  `@gravity-ui/icons/svgs/<name>.svg`. Three rules, all learned the hard way. A mis-transcribed
  path draws *nothing* while every existence assertion stays green, so re-render
  `VisualCheckIconSetTest` and look at the PNGs (`build/reports/visual/icon-set-*.png`):
  1. **One argument per source `<path>` element.** Merging an icon's paths changes how overlaps
     fill and can hollow the glyph out.
  2. **Space the arc flags out.** Compose's parser rejects SVG's compact form
     (`a.75.75 0 011.06-1.06`); each flag has to be its own token (`a .75 .75 0 0 1 1.06 -1.06`).
  3. **Strip `<defs>` first.** A few Gravity glyphs wrap themselves in `<g clip-path="url(#…)">`
     and define that clip as a `<path>` inside `<defs>` — a plain 16x16 rectangle. Ported as a
     drawn path it fills the whole box: `chart-column` first rendered as a solid black square, and
     no assertion could have caught it.
  - A fourth trap that only a picture catches: **an SVG the owner sends may be the FILLED variant**.
    The Resources page-and-magnifier arrived that way, and using it for both states rendered two
    identical solid blobs while every assertion passed.

### 3b. The target card's numeric row (2026-08-14)

Four faults the owner found in one screenshot, all of which passed every assertion:

- **The inner progress bar is Guava, not Kale**, and only turns teal at 100%. The web has always
  drawn it warm (`ProgressBar.tsx`); Android had it teal, so the one measure meant to stand out on
  an opened card was the same colour as the card's own chrome. The **card strip** across the top
  stays teal on both surfaces — that one is not the same bar.
- **The ± buttons are wide, softly rounded, white, with a hairline Kale border and a Kale sign.**
  Not grey squares with a 2dp border. They stay split, one either side of the bar.
- **The value fields are measured to their own text** (`textWidth`, a `rememberTextMeasurer`).
  A `BasicTextField` takes every pixel offered, so a fixed 64dp box left "65" floating in the
  middle and "65 / 54 kg" read as four things scattered across the row; a bare `widthIn(min=…)`
  made each field claim a whole line. `InlineEditText` **also** calls `fillMaxWidth()` whenever its
  text is centred — so these fields are deliberately left-aligned.
- **The row is a `FlowRow`, and "(from …)" is one item inside a `Row`.** Seven pieces do not fit
  across a phone; a plain Row squeezes the last ones to nothing, and ungrouped the wrap fell
  between "(from" and its number.

### 4. Verify UI changes visually before shipping

Existence-only assertions lie: a drawer once rendered with half its content pushed off-screen
while `assertExists` stayed green. **Any visible UI change must be verified by looking at
pixels** before distributing: render the changed surface in one of the
`android/app/src/test/java/com/spiramindscape/android/ui/VisualCheck*Test.kt` classes (each writes
PNGs to `app/build/reports/visual/`) and open the image, or screenshot the emulator (`adb exec-out
screencap`). Never claim a visual fix without having seen it.

> The `VisualCheck*` suite used to hang; **it doesn't any more** (BUG-009, fixed 2026-08-07 — the
> cause was `animateScrollToPage` on a tab tap, whose animation never settles under Robolectric, so
> nothing after a screen switch could reach idle). A full `:app:testDebugUnitTest` sweep now runs
> clean in **~9 minutes**; every class still re-inits Robolectric NATIVE graphics under
> `forkEvery = 1`, so prefer a single `--tests "...VisualCheck<one>Test"` class while iterating.
>
> **If a run ever wedges in `> Task :app:testDebugUnitTest` again**, stop it with
> `cd android && ./gradlew.bat --stop` — then, rather than guessing, take a **thread dump**
> (`jstack <pid>` on the newest `java` process) and read the `SDK NN Main Thread` frames. That is
> what identified the cause above in one shot; `mainClock.autoAdvance = false` and `forkEvery = 1`
> had both been tried against it and neither could work.

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

**The web menu is the standard** (updated 2026-08-08, superseding the earlier
"generously rounded card" reference). Android must look like `src/components/ui/dropdown-menu.tsx`,
because side by side the two used to read as different components: the web menu is compact and
barely rounded, while Android's was a 20dp pill with 20dp/13dp rows. When the two disagree, the
**web wins** and `SpiraDropdownMenu` is what changes.

The measurements, taken from the web component:

| | Value | Web equivalent |
|---|---|---|
| Corner radius | **8dp** | `rounded-md` (6px) |
| Container padding | **4dp** | `p-1` |
| Row padding | **10dp** horizontal, **9dp** vertical | `px-2 py-1.5` |
| Row corner (hover/press) | **6dp** | `rounded-sm` |
| Row type | `bodyMedium` | `text-sm` |
| Minimum width | **168dp** | `min-w-[8rem]` |
| Border | **1dp** hairline (`SpiraBorder`) | `border` |
| Shadow | `Modifier.shadow` **6dp**, low-alpha ambient + spot | `shadow-lg` |

**The shadow is drawn by hand, not by `Surface(shadowElevation = …)`** (2026-08-13). Android's
elevation shadow paints at full black: at 12dp it was a dark, tight band hugging the corners that
read as a grey outline round the card, and on the pale page it was the loudest thing on screen.
`SpiraDropdownMenu` now passes its own `ambientColor` / `spotColor` at ~8% / 12% ink so the
framework cannot paint it at full strength. If a menu looks like it has a border round its border,
this is what to check.

Plus the rules that don't change:

- **Pure white** background (`SpiraSurfaceRaised`), never tinted or elevation-grey.
- **Width fits its content** (`IntrinsicSize.Max`) above that minimum — never the full screen width.
- Each row (`SpiraMenuItem`) = **an icon on the left, then the label**. The icon slot is a fixed
  16dp whether or not the row has one, so every label starts at the same x. (This used to be the
  other way round — icon in a right-aligned column — which drifted each mark away from its word.)
- **Destructive** items (Delete) are red (`colorScheme.error`). A **selected** item is a **filled
  teal row with white label and white icon** — not a check mark on the far side; a filled row is
  readable at a glance.
- Anchored just below its trigger, right-edge aligned, flipping above near the screen bottom;
  dismiss on outside-tap / back.
- **Never flush against a screen edge.** The position provider keeps **12dp** from every border.
  Clamping x to `0` let the attach menu touch the left edge, which reads as a rendering fault
  rather than as a floating card — that clamp is the bug to avoid, not a style preference.

If an Android menu doesn't match the table above, it's wrong — fix `SpiraDropdownMenu`, don't ship
a different-looking menu.

### 7. Sort and filter chrome (hard spec, 2026-08-13)

A list's sort and filter controls are **not buttons**, and their menus are **not lists**. Both
surfaces implement the same thing — Android `ui/components/SpiraListToolbar.kt`, web
`src/components/spira/ListToolbar.tsx` — and neither may grow its own variant.

- **A trigger is a word, then a small solid chevron** (`SpiraIcons.ChevronDownSolid` /
  `ChevronDownSolid`), in **Kale**, with no border, no fill and no pill. It is teal in **every**
  state, *including when nothing is chosen* — never near-black, never a chip that lights up once a
  filter is on. That old treatment made an untouched toolbar the heaviest row on the page.
- **The sort trigger's word is the active key** ("Deadline"); the filter trigger's is "Filter",
  with the number of narrowing filters **in brackets** — "Filter (2)" — and nothing at zero.
- **A menu is columns**: one column per question, under its own small heading, separated by a
  **vertical hairline** (`SpiraMenuColumns` / `SpiraMenuGroup` / `SpiraMenuColumnDivider`, and the
  web twins). Sort asks two questions (key, then direction); the target filter asks three.
- Column rows are `SpiraMenuChoice` / `MenuChoice` — **no icon gutter**, because three columns each
  reserving 26dp for a mark none of them carries will not fit across a phone. Selection is the same
  filled teal row the list menus use.
- **The target filter's three questions** are independent, and a target must pass all three:
  **Status** (All / Done / Not done / Started / Not started) · **Deadline** (All / Overdue / Not
  overdue / No deadline) · **Lock** (All / Locked / Unlocked). "Overdue" follows the card's own
  rule — past *and* not yet achieved — so a target finished late is not listed as overdue.
- **Options has no sort** (position is the meaning of that list). Its toolbar is **Reorder on the
  left, then the lean filter**: All / Good idea / Bad idea / Didn't try, the first two carrying the
  smiley glyphs the card's badge uses.
- **Reorder is unavailable while a list is narrowed** by a search or a filter, on both surfaces. A
  drop sends the card's index in the *rendered* list as an absolute `position`, so on a filtered
  list the wrong order is saved with nothing on screen to say so.

> Verifying a menu on Android needs care: a `Popup` renders in **its own window**, which the
> `VisualCheck*` screenshot helper (it draws the activity's decor view) cannot capture — an open
> menu is simply absent from the PNG. Render `SpiraMenuSurface` directly instead, as
> `VisualCheckToolbarMenusTest` does, or the one check that would catch a third column hanging off
> the screen silently checks nothing.

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

> **The body face is switchable at runtime while the owner picks one** (GRO-122, 2026-08-13):
> **Settings → Fonts** offers GCentra plus Toronto, Guidy, Kalamayka, Leggibilmente, BIM and
> Spartan, and a tap re-fonts the whole app. It moves **only** the body face — headings stay ITC
> Clearface — and it is **not** the swap procedure: see `docs/changing-fonts.md` → "The Fonts tab".
> Keep the web (`src/lib/spira/app-font.ts`) and Android (`ui/theme/AppFont.kt`) lists identical,
> or the phone and the laptop stop being comparable, which is the whole point of the tab.

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
  accent. Small accent **marks** in Guava are fine (e.g. the GROW tab-bar underline, or the "good
  idea" smiley on an Options card).
- **NEVER use white copy on a light background colour.**
- **NEVER use black / `#222525` copy on Kale** — text on teal is white/light.
- **NEVER use Guava as a text colour over Kale.**
- Don't mix colours in ways that hurt legibility; keep combinations from the approved pairs.

An Options card's **"good idea" smiley** is **Guava** (`tertiary`) on a white badge — a small
accent mark, not a fill (an allowed accent use of Guava).

Semantic mapping already wired in `Color.kt` → `Theme.kt`: primary = Kale-500, accent/tertiary =
Guava-500 (accent marks only, no large fills), foreground = Salt-1000, muted = Salt-800, border =
Salt-500, background = Parsnip-100, cards/menus = White, destructive =
Guava-600. (`success` is a functional green — new work should take it from the **success** ramp
above rather than picking a fresh green.)

> The type params above (**leading / tracking / alignment**) are **font-independent** — they're set
> on the type scale (`Type.kt`) and per-usage alignment, so they hold no matter which heading font
> ships. Don't tie them to a specific font.

### Goal-workspace navigation (Android)

Inside a goal the chrome is **not** the All-goals `SpiraTopBar` — that header is unchanged on the
dashboard. The workspace has its own, in `ui/components/GoalWorkspaceChrome.kt`:

- **Header** (teal): a **chevron in a circle** on the left → back to All goals; a **goal search
  field** in the middle (it switches goals, and its results hang under the header as a white
  card); an **X in a circle** on the right → delete the goal, behind a confirm dialog.
- **GROW tab bar** under the header, on **every** phase screen: `Goal · Reality · Options ·
  Will do`, the current one marked by a **Guava underline**. Tapping a tab and swiping the pager
  drive the same state. On the Resources page nothing is underlined (`selectedIndex = -1`) — it
  isn't a GROW phase — but the row stays, so one tap leads back into the flow.
- **Footer**: **menu** (opens the drawer) · **AI coach** (the sparkle) · **Resources**. All three
  marks are the page's own near-black ink — the sparkle sits a little larger, but it does **not**
  take the `intelligence` violet: one coloured item in a row of three read as a badge stuck to the
  bar rather than as a place (2026-08-13). The current item switches to the **filled** twin of its
  glyph. The assistant also opens by **swiping up on the footer**, and closes with its own button,
  the back gesture, or by dragging its top handle down — see `AiChatHost`. There is deliberately
  **no horizontal swipe** between the chat and the page: horizontal is the tabs' axis.
- **Resources is a page**, not a tab and not a drawer. Its footer mark is a **page with a
  magnifier**, and it is deliberately a *different* glyph from the drawer's **Knowledge**
  (an open book): the footer holds what is attached to *this* goal, Knowledge holds links and
  reading kept across the app. One mark for both had the two reading as the same place.

The **drawer** (`SpiraDrawer`, shared with the dashboard) follows two rules of its own:

- **A rubric with sub-items is never marked as "you are here" — only the open sub-item is.** The
  goal's own name is a heading over places, not a place you can be; lighting both said the user
  was in two places at once and left the eye nothing to land on.
- **The open sub-item marks itself with a Kale line.** The rail beside the sub-items is drawn
  **per item**, not as one line down the side — a shared line could only ever be one colour. Each
  item's own stretch is a hairline in the border grey, and the open one is the **full lane in
  Kale** (`RAIL_LANE` / `RAIL_HAIRLINE` in `GoalsDashboardScreen.kt`). The lane keeps its width in
  both states, so lighting an item never nudges the words.
- The **account** is not in the drawer's list. The figure in the All-goals header opens the
  **Settings page** (`ui/settings/UserSettingsScreen.kt`) — a page, not a sheet — which is where
  the address and Sign out live. That figure used to open this same drawer, so the app had two
  buttons for one action and nowhere at all to see or leave your account.

**The search box is screen-local.** It starts empty on every visit and is never shared with the
dashboard's filter — a search typed on one screen must not follow the user onto the next. The web
enforces the same rule through `useResetQueryOnNavigate` (`shell-store.ts`), because there one
`query` field backs both searches.

Phase screens open with the shared **`GoalTabIntro`** block (`GoalWorkspaceScreen.kt`): a
**centered heading + centered description**, with **clear space between the two** (the brand
"clear space between header and body" rule) and the same top gap on every screen, so moving
between phases never shifts the type. There is no coloured kicker above it — the GROW tab bar sits
directly above the block and already names the phase. The Goal screen leads with the editable goal
title instead.

### AI proposal cards — the web is the spec, verbatim

`src/components/ai/AiPanel.tsx` defines this family (`ProposalCard`, `CreateConfirmCard`,
`CreateChecklistCard`, `SteppedProposalCard`, `ResultSummary`) and the Android port in
`ui/ai/ProposalCard.kt` **copies it**. There is nothing to redesign here: if the two differ, the
web wins and Android changes. Android had quietly drifted on all of the following, and every one
of them read to the owner as a redesign nobody asked for (2026-08-14):

- **A pending card IS the input.** It renders in the footer, **where the composer would be**, and
  the composer is hidden while it waits. It does not sit inline in the transcript, where it can
  scroll out of sight while the chat waits on it.
- **Once answered, the card is gone.** All that stays in the transcript is `ResultSummary` — one
  compact pill per approved change, with an **Open** shortcut to what it made.
- **Buttons are not a row of three.** `Accept` (filled Kale-600) and `Edit` (outlined) sit together
  on the left; **`Dismiss` is a quiet word pushed to the right**, never a third button. Refusing a
  change must not carry the same weight as making it.
- **Several changes are a review, not a queue.** One card: `N CHANGES`, `i / N`, a progress rail, an
  X that dismisses the lot, a tick per step, Back/Next, and **one** `Save all N` button plus a quiet
  `Dismiss all`. Not per-step Accept/Dismiss.
- **The kind is an uppercase kicker** (10.5sp, Kale-600, with a 12dp mark) — not a `SpiraBadge` pill.
- **Creates get their own shapes**: a bare create is a one-tap confirm card; a create carrying
  optional fields is a checklist of ticks (the entity, its checklist items, then each optional
  field), with a rule above the footer.

The web's class values are copied into the Kotlin as the measurements to match — card
`rounded-[14px] p-4`, buttons `rounded-[9px]`, headline 17sp serif, detail 13.5sp at 60% ink. Read
them from `AiPanel.tsx` before changing anything here.

### Options cards (interaction)

**The web is the spec.** `src/components/spira/OptionsList.tsx` and the Android `OptionsTabContent`
/ `OptionCard` (in `GoalWorkspaceScreen.kt`) render the same thing; when they disagree, the web
wins and Android is what changes. Android used to have a teal full-screen Options page with
centered "Option N" cards, a half-oval "bump" menu and a Guava corner-check ribbon — all of that is
**gone** (2026-08-10). Don't bring any of it back.

- The Options page uses the **ordinary off-white page background**, like every other phase screen.
  It is not a teal screen.
- **The user-facing word is "option", never "strategy"** (2026-08-13) — the search placeholder is
  "Search options", the create form says "New option", and every label, empty state and aria-label
  follows. Only comments and test fixtures still say strategy.
- An option is a **bordered row**, not a centered card, and carries **no "Option N" number**:
  - a **48dp left cell** with the goal-wide single-select **active radio**, tinted (`primarySoft`)
    and teal-bordered when active — that radio is the only "active" marker; there is no ACTIVE
    band and no label;
  - the **inline-editable option text**, left-aligned, clamped to **3 lines** behind a
    **Show more / Show less** toggle. That toggle is a **worded link on its own line** under the
    text (`Show more` / `Show less`) on **both** surfaces — never a chevron floated over the last
    line, which covers the words it is hiding;
  - the shared **`ElementActionsMenu`** (⋯ → Attach resource / Delete). It is **not** part of the
    resting card: it appears only when the option **text** is tapped for editing, with the
    caret, and floats over the text's top-right corner rather than taking a column of its own —
    an always-present column would sit empty, and appearing would reflow the words being edited.
    It stays while its dropdown is open, so losing the caret can't remove it mid-tap;
  - a **smiley badge** half off the card's top-right corner that cycles the thumb lean on tap:
    none (grey outline) → **good idea** (Guava `Smile`) → **didn't work** (Kale `Frown`). It is
    independent of the active radio — an option can be both. Both smileys are Gravity's
    `face-smile` / `face-sad`: **outline faces with solid eyes**. The owner's earlier glyphs were
    solid all through and the lighter pair was approved on 2026-08-14. The rule that has not
    changed is the eyes — never a mark whose eyes are hollow rings, which at 16dp read as a
    rendering artefact rather than as a face.
- **New options are typed into the "Add an option…" field at the foot of the web list**; Android
  creates one from its `NewOptionSheet`. Neither surface has a "+" FAB on this tab.
- **Reorder is a mode**, not a long press: a **Reorder / Save** control sits at the LEFT of the
  toolbar with the lean filter beside it, from two options up; while it is on, the whole card drags
  and every per-card control goes inert. It disappears entirely while a search or the lean filter
  narrows the list — see the sort-and-filter spec above for why.

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

### Linear mirror

The owner keeps a Linear workspace — **GROW goals** (https://linear.app/grow-goals) — as a
readable copy of the project's tracker and documentation, so the material can be browsed, filtered
and shared outside the repo. One team, **GROW goals** (key `GRO`); the repo's material lives in the
project **Spira backlog**
(https://linear.app/grow-goals/project/spira-backlog-4512ed68f7ee).

**The repository is always the source of truth.** Linear is a hand-maintained mirror: nothing
reads from it, and a stale issue there is a documentation problem, not a code problem. Never
resolve a question by trusting Linear over the files.

What the project holds (state as of 2026-08-10):

| Linear object | Mirrors | Depth |
|---|---|---|
| **Issues** in *Spira backlog* | `backlog/` — **all 38** bug files, one issue each | **full text** of the file in the issue description |
| The **Docs and Specs index** document | `docs/` (33) + `specs/` (19) | **index only** — one page of titles + GitHub links, no body text |
| Other project documents (Icons, Pills, Dashboard, …) | — | the owner's own design notes, not mirrored from the repo |

The bug mirror is **complete** as of 2026-08-10 (`GRO-82`…`GRO-119`) — every file in `backlog/`
has an issue, and every one of those issues carries a link attachment pointing at its file on
GitHub. Keep it that way: a new bug file without an issue is a gap, not a style choice.

**Docs and Specs index** (https://linear.app/grow-goals/document/docs-and-specs-index-4991a478904d)
is a **single** document, not one document per file: two tables (Docs, Specs) of repo-relative path
→ title, each path linking to the file on GitHub `main`. Each dated spec **folder** is one row, not
one row per file inside it. Because it holds links rather than copies, it goes stale only when a
file is **added, removed or renamed** — not when its contents change.

> **Issues in `Todo` are the owner's own queue — leave them alone.** The owner files them directly
> (often in Russian, e.g. `GRO-79`…`GRO-81`): small fixes to be done **on request**, not standing
> bugs. They are **never** mirrored into `backlog/`, never get a `BUG-nnn` id or a backlog file, and
> their absence from `backlog/` is **not** a gap in the mirror. Don't rewrite, re-label, re-state or
> close them, and don't start work on one unasked. When the owner does ask, fix it and leave the
> issue's state for them to change.

Conventions for the mirrored issues, so a re-sync stays consistent:

- Title is prefixed with the `BUG-nnn` id where the file has one; the few backlog files that
  predate the ids keep their own title.
- The description opens with a two-column metadata table — **Repo status**, **Area**, **Severity**,
  **Source** (the repo-relative path of the backlog file) — then the file's body verbatim.
- Every issue also carries a **link attachment** to its file on GitHub `main`
  (`save_issue`'s `links`, titled with the repo-relative path). Links are append-only, so re-sending
  one is harmless.
- Linear takes **real Markdown**: hard-wrapped paragraphs, pipe tables and code fences all go in
  as they are, with no reshaping. Pass literal newlines, never escape sequences.
- State maps `🐞 Open → Backlog`, `🔧 In progress → In Progress`, `✅ Fixed → Done`. The team also
  has `Todo`, `Canceled` and `Duplicate`.
- **Labels** carry the classification: a type (`Bug` / `Feature` / `Improvement`)
  plus area labels (`Security`, `Accessibility`, `Tests`, `Performance`, `Design`, `AI`, `CI`,
  `Backend`, `Android`, `Web`, `Resources`, …). Reuse what exists; don't invent a near-duplicate.
  `save_issue`'s `labels` **replaces the whole set**, so always pass every label that should remain.
- **Cycles are the sprint** (they replaced the old planning label — there is no `Sprint` field).
  **Put every issue we work on into the current cycle** (`save_issue`'s `cycle`), and leave the
  cycle of an issue we didn't touch exactly as it is — this rule adds, it never rewrites someone
  else's. Read the current one with `list_cycles(teamId, type: "current")`; never guess a number.
  ⚠️ **The team's cycles are currently switched off** — the last, **#30**, ended 2026-06-21 and
  `type: "current"` returns nothing. Cycles can only be re-enabled by the owner in Linear
  (Settings → Team **GROW goals** → Cycles); the MCP cannot create one. Until a current cycle
  exists, set no cycle at all and say so rather than inventing a substitute.
- The Linear MCP has **no delete operation for issues or documents** (only for attachments,
  comments and status updates) — to retire an issue set its state to `Canceled` or `Duplicate`, or
  ask the owner to archive it in the UI.

**When a backlog file's `Status` changes, update the matching issue's state too** — or tell the
user plainly that the mirror is now stale. Same for adding a new bug file: it needs a new issue.
And when a file is added to or renamed in `docs/` / `specs/`, fix its row in the Docs and Specs
index document.

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
