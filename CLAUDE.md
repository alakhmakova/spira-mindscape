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
- ⚠️ **A hook enforces this on the owner's machine, and only there** (corrected 2026-08-30).
  `.claude/settings.json` registers a `PreToolUse` hook on Bash — `.claude/hooks/block-git-write.js`
  — which exits 2 on every writing form listed above. It fired for real on 2026-08-30.
  - **But `.claude/` is gitignored** (`.gitignore` line 26) and none of it is committed, so a
    fresh clone or another machine has **no** hook and nothing but this rule. The rule is the
    authority; the hook is a safety net that may not be there.
  - This entry has now been wrong in both directions: it once claimed a hook that did not exist,
    and was then "corrected" to deny the one that does. The fact that reconciles them is that
    the file lives **on disk and not in git** — run `ls .claude/` before believing either claim.
  - **It matches the raw command text, not the intent**, and scans the whole string so
    `cd x; <write command>` is caught too. That also means a heredoc *quoting* one of those
    commands is blocked — editing this very section from Bash fails. Use Edit/Write for it.
  - The same untracked `settings.json` runs the `Stop` hooks named below, so if lint and the
    unit tests never seem to run at the end of a turn, this is why.

When work is ready, summarize what changed and let the user commit.

### Cutting a release (hard order, owner 2026-08-30)

**A GitHub release is a tag, and the hook does not catch it.** `gh release create` publishes a new
tag on the remote — the thing the ❌ list forbids — but it is not one of the patterns
`block-git-write.js` matches, so nothing stops it. Only this rule does: **the owner asks for the
release and picks the ref and the version.** Never cut one unprompted.

**The order below is not a preference. Done out of order it costs a deleted, re-published tag**,
which is what happened on 2026-08-30: `v0.3.0-alpha` went up first, its tree still said
`versionName 0.2.7`, and the whole release had to be destroyed and recreated an hour later.

1. **Agree the version with the owner**, and the ref. Read the existing tags first —
   `git for-each-ref --sort=-creatordate refs/tags` — because `gh release list` shows **nothing**
   when tags were pushed without releases attached, which is how this repo's first four look.
2. **Bump the version in the code and let the owner commit it.** The number lives in exactly
   **one** place: `versionName` / `versionCode` in `android/app/build.gradle.kts`. Not
   `backend/pom.xml` (permanently `0.0.1-SNAPSHOT`) and not `package.json` (no `version` field at
   all). `versionName` is the tag without its `v` and `-alpha`; `versionCode` only ever goes up.
3. **Wait for that commit to reach `main`** — every tag in this repo sits on a `mobile → main`
   merge commit — and **wait for its CI to go green**. `gh run list --branch main --limit 3`.
   A release pointing at a red or a not-yet-merged commit is worse than a late one.
4. **Only then create the release**, from the full 40-character SHA. A short SHA is rejected with
   `HTTP 422 … Release.target_commitish is invalid`; a branch name works but records whatever the
   tip is at that instant, which is not the same promise.
5. **Write the notes from the range, never from memory** — `git log <prev-tag>..origin/main`,
   `git diff --name-status <prev-tag>..origin/main -- backlog` for the bugs actually closed (and
   which are still `🐞 Open`), and the added files for what is genuinely new.

**If it is already published at the wrong commit**, the target cannot be edited: GitHub ignores
`target_commitish` once the tag exists. It has to be `gh release delete <tag> --cleanup-tag --yes`
and then created again — a public artefact deleted and re-announced. Get step 2 right instead.

### The one carve-out: history surgery (owner, 2026-08-23)

Removing something that should never have been committed — a leaked secret, a copyrighted
file — is work the agent **may** do, because it is a mechanical, reviewable local operation
and the remote still holds the original until it is pushed.

- ✅ The agent may run `git filter-repo` (or BFG) and inspect the result: `git log`, `git
  show`, `git cat-file`, verifying the paths are gone.
- ❌ **`git push` stays absolutely forbidden, `--force` included.** That is the irreversible,
  public step, and it is the owner's to take. The agent prints the command; the owner runs it.
- Before any rewrite: the working tree must be committed or stashed **by the owner**
  (`filter-repo` refuses to run on a dirty repo), and the agent must state plainly what will
  change — every SHA from the first affected commit onward, a force-push required, and any
  other clone of the repo needing a fresh clone rather than a `git pull`, or it will drag the
  removed files back in.

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
   - The `Stop` hooks also run lint/typecheck + fast unit tests and will surface failures — when
     they are installed. They live in the **untracked** `.claude/`; see the commit policy above.
   - **A green `curl` is not proof the browser works.** curl sends no `Origin`, runs no JavaScript,
     keeps no cookies and obeys no CSP. A phone on a tunnel once got **403 on every GraphQL call**
     while `curl` and the page load both answered `200` — the screen said "We couldn't sync with the
     backend" and nothing pointed at CORS. Anything touching **proxies, CORS, auth, headers or
     cookies** has to be checked from a real browser (Playwright will do), and anything visual has
     to be checked by looking at pixels (Design → Components and chrome → 4).
   - **Moved a handler or renamed an `aria-label`? `grep` `e2e/` in the same change.** The
     Playwright specs press *exact* interaction targets — which element receives `pointerdown`,
     which accessible name a control answers to — so an interaction redesign silently invalidates
     them. `ff3f519` moved the options drag from the whole card onto the left-slot **grip** (a
     correct fix: the card had been stealing every swipe from the page scroll) and did not touch
     `e2e/`; the drag test kept pressing the card's centre, where there is no longer a handler at
     all, and CI failed on the next PR. `grep -rn "reorder" e2e/` would have found it in one
     second.
   - **Three identical retries are not flake.** Playwright retries twice; genuine infrastructure
     trouble gives *different* tests or *different* errors each time. The same test failing on all
     three attempts with the same message means the product moved and the spec did not — read the
     diff, don't re-run.
   - **E2E doesn't run in the `Stop` hooks** (they cover lint/typecheck + fast unit tests), and
     `npm run test:e2e` needs the full stack up — Docker Postgres + the backend on the `local`
     profile + Vite (see Build / run reference, and the `run-spira` skill). That is precisely why
     a broken spec reaches CI instead of the desk it was broken on: **after a web interaction
     change, bring the stack up and run at least the affected spec** —
     `npx playwright test e2e/<file>.spec.ts`.
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

## Hosting a WebView in Compose (hard rule)

**An `AndroidView` factory must never return a `WebView`. Return a plain `FrameLayout` with the
WebView as its child.**

Compose treats the view a factory returns as its own: `AndroidViewHolder` takes it into Compose's
focus system and drives its layout. A WebView does not survive that. Chromium answers by calling
`ImeAdapterImpl.cancelComposition()` → `InputMethodManager.restartInput()` **on every keystroke**,
which destroys the IME's composing region. Everything that follows looks like four separate bugs and
is one (measured on an emulator, 2026-08-21 — BUG-041, after an earlier investigation had spent ten
hypotheses on the page):

| What the user sees | Why |
|---|---|
| The caret jumps to the start of the note | Each fresh `EditorInfo` says `initialSelStart=0`, and ProseMirror follows the DOM selection |
| Every letter comes out capitalised | The IME asks `getCursorCapsMode` after each restart and, believing the caret is at 0, is told "start of a sentence" |
| A fragment is duplicated on a new line | With no composing region, GBoard's next `setComposingText` **inserts** instead of replacing — "world" becomes `WWo…` |
| The keyboard covers what is being typed | The reported cursor rect stays `Rect(0,0-0,0)`, so nothing can scroll the caret into view |

The numbers, typing `hello world` after `START.` on the emulator's real keyboard
(`adb shell dumpsys input_method | grep initialSelStart`, where 6 is correct):

| Host | `initialSelStart` | Result |
|---|---|---|
| Plain Activity + `FrameLayout` | 6 | `START.hello world` |
| **`AndroidView` returning the WebView** | **0** | **`WWoLDSTART.HELLO `** |
| `AndroidView` returning a `FrameLayout` | 6 | `START.hello world` |

`NoteEditorWebViewHostTest` guards it, because **both shapes compile and render identically** — the
difference only appears under a real IME, and `adb shell input text` bypasses IME composition, so it
cannot reproduce it either. Only tapping the on-screen keyboard can.

**Keeping the caret clear of the keyboard is the page's job, not CSS's.** Chromium scrolls a focused
editable back into view when the IME opens, but only to *just* inside the bottom edge, so the line
being typed ends up touching the keyboard. `scroll-padding-bottom` on the scroller is the right tool
and was **measured as a no-op** in this WebView (Chromium 109 does not consult it on that path:
`innerHeight - caretRect.bottom` stayed exactly 0 with it applying). `keepCaretClear` in
`embeds/note-editor/main.ts` does it instead — after the browser's own scroll, only ever downwards,
scroll container only so it is safe mid-composition.

**Two things that make this class of bug findable at all**, both already in place:

- `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)`, and CDP reached over
  `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>` — that gives the page's real
  `beforeinput`/`composition*` trace and lets the Java bridge be switched off (`window.SpiraNote =
  undefined`) without a rebuild, which is how the bridge was **excluded** as a cause.
- `app/src/debug/` — a manifest overlay exporting `NoteEditorActivity` so it can be launched from
  `adb` with content already in it, and `debug/NoteEditorProbeActivity.kt`, which hosts the same page
  four ways (`bare` / `compose` / `column` / `touch`) and separated "the host" from "our
  customisations" in three runs. Both are debug-only; note that debug builds are what App
  Distribution sends to the owner's phone.

---

## 🎨 Design — every visual rule, in one place

**Everything about how Spira looks lives in this section and nowhere else in this file.** It used to
be three top-level sections with unrelated material between them, so a reader had to know all three
existed to be sure they had read the rules (owner, 2026-08-21). If you add a visual rule, add it
here.

Both surfaces — React web and native Compose — mirror **one** design. A rule below is a rule for
both unless it names a surface.

| Look for | It is under |
|---|---|
| Never using a raw platform widget | Components and chrome → 1 |
| Inline editing on the goal page | Components and chrome → 2 |
| Icons (Gravity UI), and no emoji | Components and chrome → 3 |
| The target card's numeric row | Components and chrome → 3b |
| **Pills** — the one capsule shape | Components and chrome → 3c |
| **Notices and toasts** — the one message card | Components and chrome → 3d |
| **Sheets** — the drawer, the side panel, the two head types (Kale / white) | Components and chrome → 3e |
| **Dates** — a modal on a phone, a popover on a laptop; ISO weeks | Components and chrome → 3e-ter |
| **Sheet heights** — a constant top edge, never a percentage | Components and chrome → 3e-bis |
| **What a headless host cannot see** — dialog width, the keyboard | Components and chrome → 3e-quater |
| **Search fields** — and the word that empties one | Components and chrome → 3f |
| Checking a UI change by looking at pixels | Components and chrome → 4 |
| Menus and overlays are pure white | Components and chrome → 5 |
| Dropdown / kebab menu anatomy | Components and chrome → 6 |
| **Sort and filter** — the panel, and the padlock | Components and chrome → 7 |
| Typography and the brand fonts | Brand → Typography, Font loading |
| **Colour** — the palette and the full ramps | Brand → Colour |
| Progress bars — exactly four variants | Brand → Progress bars |
| The goal workspace's own navigation | Brand → Goal-workspace navigation |
| AI proposal cards | Brand → AI proposal cards |
| Options cards | Brand → Options cards |
| Importing a screen from claude.ai/design | Claude Design |

---

### Components and chrome (hard rules — web *and* Android)

These are **non-negotiable** and apply to every surface (React web, native Compose).

#### 1. Never ship raw, un-customized default elements

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

#### 2. Inline inputs (the goal-page editing pattern)

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

#### 3. Icons & emoji

Per `specs/2026-06-07-ai-assistant-cards-and-drawers/requirements.md` and the icon convention in
`specs/tech-stack.md`:

- **No emoji anywhere** — not in UI text, notifications, empty states, badges, or AI/assistant
  replies. (Use a word like "Achieved", not `✓`/`🎉`.)
- **Both surfaces are on Gravity UI** (MIT, (c) 2022 YANDEX LLC) — **every glyph**. Android ported
  first (2026-08-14, `ui/icons/SpiraIcons.kt`), the web followed the same day
  (`src/components/spira/icons.tsx`). `lucide-react` is **removed** (dependency and all imports),
  the hand-drawn `brand-icons.tsx` is **deleted**, and the AI panel's inline Lucide path map is
  Gravity too. Gravity was measured against five other collections in **`specs/icon-sets.md`** and
  was the only exact match for the drawing system Spira's look was modelled on: a **16 box**, a
  **1.5 wall**, `.75` radii, the `1.06` diagonal, and the line drawn as an even-odd **fill** rather
  than a stroke.
  - **Android** has exactly one builder, `gravity()`; the Iconoir, Phosphor and hand-drawn builders
    are gone, and with them the ink-weight corrections they needed — one set cannot disagree with
    itself, which is what `PHOSPHOR_INK_BOOST` existed to paper over.
  - **Web** has one module, `src/components/spira/icons.tsx`: each icon is a `make(...)` component
    on a `0 0 16 16` viewBox with `currentColor` fill, a drop-in for the Lucide component it
    replaced (same `className`, same `size`). Import icons from there — never add `lucide-react`
    back, and don't hand-draw SVGs in a component.
  - **The two surfaces move together now.** A glyph the owner picks goes to **both**; the web module
    mirrors the Android names where they overlap (`Trophy` → `chart-column`, `SlidersHorizontal` →
    `bars-descending-align-center`, and so on) so a change lands the same on the phone and the laptop.
  - **Take the glyph Gravity already has.** If it has nothing that suits, **ask the owner** rather
    than drawing one or reaching into another set. Six marks had no Gravity equivalent (a brain, a
    marker, a calendar-plus, a leaf) and the owner chose the substitutes.
  - **One glyph, one name, and the name must describe the glyph** (Android). No `Nav` prefix: it
    used to mean "the 16-grid twin of a 24-grid icon" and means nothing now that there is one set. A
    name that survives its glyph is a defect — `NavTrophy` drew a bar chart, `SlidersHorizontal`
    drew aligned bars, `ChevronDownSolid` drew a caret. (The **web** module keeps the old Lucide
    export names on purpose, so the port stayed a mechanical import swap; the glyph each draws is
    Gravity's.)
- **Never put a solid mark in a column of outline ones.** Gravity's `-fill` twins exist for exactly
  one purpose: marking the **ON state of a toggle next to its own outline sibling** — the selected
  footer item (`FolderOpen` / `FolderOpenFilled`), and the **closed padlock**
  (`lock-fill` / `lock-open`, owner 2026-08-21) that keeps a list's filters and sort. The drawer's
  trophy was once a filled cup beside five hollow glyphs and it was the loudest thing on the sheet.
  - The padlock is the case that shows *why* the twins exist. Drawn as two outlines, locked and
    unlocked differed only in whether the shackle hung open — a couple of pixels at 16px, which you
    had to go looking for. Solid-when-closed against outline-when-open reads at a glance. Both
    surfaces draw the pair: `LockFilled` / `LockOpenFilled` on the web, `SpiraIcons.Lock` /
    `SpiraIcons.LockOpen` on Android.
- **A sortable column that is not the sorted one shows `carets-expand-vertical`** — Gravity's
  double caret (owner, 2026-08-21). It used to be a faint `ChevronUp`, which does not say "you can
  sort by this"; it says "sorted ascending, quietly", and next to the column that genuinely *was*
  ascending the only difference was opacity. The double caret has no direction to misread. The
  **active** column keeps a single chevron in Kale, because there the direction is real
  information. (Web: `SortIcon` in `Targets.tsx`.)
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

#### 3b. The target card's numeric row (2026-08-14)

Four faults the owner found in one screenshot, all of which passed every assertion:

- **The inner progress bar is Guava, not Kale**, and only turns teal at 100%. The web has always
  drawn it warm (`ProgressBar.tsx`); Android had it teal, so the one measure meant to stand out on
  an opened card was the same colour as the card's own chrome. The **card strip** across the top
  stays teal on both surfaces — that one is not the same bar.
- **The ± buttons are the web's exact button** (`Targets.tsx`): a **36dp square, `rounded-md` (6dp),
  a 2dp grey border, a near-black sign**. They stay split, one either side of the bar. (A brief
  detour drew them wide with a Kale border from a reference screenshot; the owner then asked for the
  Android card to match the web, so they are the grey square again — when the two disagree, the web
  wins.)
- **The value fields are measured to their own text** (`textWidth`, a `rememberTextMeasurer`).
  A `BasicTextField` takes every pixel offered, so a fixed 64dp box left "65" floating in the
  middle and "65 / 54 kg" read as four things scattered across the row; a bare `widthIn(min=…)`
  made each field claim a whole line. `InlineEditText` **also** calls `fillMaxWidth()` whenever its
  text is centred — so these fields are deliberately left-aligned.
- **The row is a `FlowRow`, and "(from …)" is one item inside a `Row`.** Seven pieces do not fit
  across a phone; a plain Row squeezes the last ones to nothing, and ungrouped the wrap fell
  between "(from" and its number.

#### 3c. Pills — one shape, and it is an OUTLINE (hard spec, 2026-08-17)

Anywhere a short word is set in a capsule — a status, a kind, a state, **or one answer of a
one-line filter question** — it is the app's one pill shape, and nothing else:

| | Value |
|---|---|
| Shape | fully rounded capsule |
| Border | **1px, the ramp's bright solid step** (`success-900 #007A4B`, `info-900 #006CC1`, `warning-900 #896500`, `error-900 #C53336`, `Kale-500`, `intelligence-900 #6E56CF`, `neutral-1200 #6B6B6B`) |
| Fill | **the same ramp's `100` step** — nearly white on purpose |
| Label | **near-black (`Salt-1000`) in every tone**, semibold, ~12px, **sentence case** |

The components are **`ui/components/SpiraBadge.kt`** (Android) and **`src/components/spira/Pill.tsx`**
(web). Use them; don't hand-roll a capsule.

Two rules that are the whole point:

- **The outline carries the meaning, not the fill and not the type.** A row of pills then reads as
  one family in different states. Colouring the word as well only makes the label harder to read.
- **A FILLED capsule is not a pill — it is a button.** The goal-status filter shipped once as solid
  green capsules and read as a row of buttons rather than as a set of answers. For a filter row the
  chosen answer takes its tone and **every other answer is `Neutral`**; that difference is what
  makes the choice legible, without a second colour.

#### 3d. Notices and toasts — one card, and the colour family is what changes (hard spec, 2026-08-21)

Every message the app shows — a transient toast **and** a notice sitting inside a block — is the
same card on both surfaces: web `src/components/spira/Notice.tsx` (`NoticeCard`, and the toast in
`src/components/ui/sonner.tsx` reads the same table), Android `ui/components/SpiraNotice.kt`
(`SpiraNoticeCard`, drawn by `SpiraToast` and `SpiraInlineBanner`).

- a **1px border in the kind's colour** over a **very pale tint from the same family**, an **8px
  radius**, and the shadow `0 4px 12px rgba(28,28,28,.08), 0 2px 8px rgba(28,28,28,.04)`;
- a **filled semantic glyph** on the left **in the border's colour**, aligned to the message's
  **first line** (not centred against a message that wraps):

  | Kind | Glyph | Border + glyph | Fill |
  |---|---|---|---|
  | Success | `circle-check-fill` | **Kale-500 `#0A8080`** | brand-100 `#F9FDFC` |
  | Error | `circle-exclamation-fill` | `error-900 #C53336` | error-100 `#FFFBFB` |
  | Warning | `triangle-exclamation-fill` | **`warning-500 #C99500`** | warning-100 `#FFFBF7` |
  | Info | `circle-info-fill` | `info-900 #006CC1` | info-100 `#FDFCFF` |
  | AI | `sparkles` | **Intelligence-400 `#BDAEFF`** | Intelligence-100 `#FEFBFF` |

  The owner gave the **blue and the green verbatim** (2026-08-21); the yellow and the red follow the
  identical rule — the ramp's solid step outlining its own `100` tint.

  **All four semantic marks are Gravity's `-fill` twins** (owner, 2026-08-18). This is the one place
  a solid mark is right in a set of four: they are one family saying one kind of thing, and an
  outline among them read as a different sort of message. It does not license a solid mark in a
  column of outline ones anywhere else.

  **Warning is a real yellow, never the brown `#896500`.** `warning-900` is brown on screen, and a
  brown triangle on a warning was rejected on sight (owner, 2026-08-18). It is the same `#C99500`
  the assistant's error turn uses.

  **AI is the one kind that breaks the pattern, and deliberately**: its border is Intelligence-**400**
  rather than a solid `900` step, because the assistant's surfaces are drawn in that violet
  throughout and a saturated outline would out-shout them. Use it only for a message *about the
  assistant* — never as a fifth way to say "success".

- **near-black text in every kind** — the border and the mark carry the meaning, not the type (the
  same rule the pills follow);
- an **X INSIDE the card, on the right** (owner, 2026-08-21) — **top-right when the message wraps,
  simply right when it is one line**. One rule gives both: the X follows the glyph's rule on the
  other side and aligns to the message's **first line**, which on a one-line card *is* the card. It
  is never the platform's floating corner circle. (On the web that means overriding sonner, whose
  own close button is absolutely positioned outside the corner — see the note in `sonner.tsx`.)

**The tint is nearly white and must stay that way.** The card still has to read as a white card on
the page; the border is what tells the kinds apart. A fill any stronger turns a message into a
block of colour, which is the thing the earlier "never a coloured block" rule was aimed at — that
rule is superseded by this table, not by a licence to tint harder.

**Styling the web toast needs `!` or a CSS variable, never a plain utility.** sonner injects its
stylesheet **unlayered**, and Tailwind v4 compiles utilities into `@layer utilities` — unlayered
beats layered whatever the specificity, so `bg-[#FFFBFB]` on a toast silently does nothing. The card
therefore sets sonner's own `--normal-bg` / `--normal-border` / `--normal-text` on the toast element
(a property declared on the element beats one inherited from sonner's container, with no layer
contest), and everything sonner hard-codes — the shadow, every close-button property — carries `!`.
This was shipped once as a no-op; the picture is the only way to catch it.

**Say what happened, not that something happened.** "Bold and italic copied — select text to apply
it" is a message that confirms; "Formatting copied" only reassures. The two surfaces word the same
event identically (`describeMarks` on the web, `describeMarks` in `NoteEditorActivity.kt`).

**A notice INSIDE a block is the same card** (owner, 2026-08-18) — an action that failed without
changing the screen, a filter that hid every row, a fact the page has to state. One picture is the
reference for both, so on Android there is one component, **`SpiraNoticeCard`**, and `SpiraToast`
and `SpiraInlineBanner` are only *where* it sits: the toast floats and times itself out, the banner
sits in the flow. Do not invent a second shape for a message — the app had four (a red-tinted error
block, a translucent white strip in the AI panel, bare red type on the sign-in and provider screens,
and a raw platform `Toast`), and every one of them said the same kind of thing differently.

Two rules that follow:

- **The kind changes the border, the fill and the mark — never the type.** Near-black words in every
  kind; colouring them as well only makes them harder to read.
- **A notice the user cannot act on has no X.** "No goals match that search or filter" stands until
  the filter changes, so a dismiss button on it would be a lie; pass `onDismiss = null`.

**A list emptied by its own search or filter is a `Warning` notice, not a grey empty state.** The
empty state is for a list with nothing in it — an invitation. A list the user has just hidden is a
different sentence, and a muted line centred in a blank page reads as "there is nothing here".

**And the notice stands on its own — never a coloured frame inside a grey one** (owner,
2026-08-22). The All-goals dashboard used to render it centred inside the empty state's
`surface-card`, so the warning outline sat within a hairline card: two frames for one message, and
the outer one still said "an empty page" while the inner one said the opposite. The card belongs to
the empty state's invitation; the filtered case replaces it rather than sitting inside it (web:
`src/routes/index.tsx`; Android already drew it this way).

#### 3e. Sheets — one shape, two heads, on both surfaces (hard spec, 2026-08-22 — heads revised 2026-09-03)

A **sheet** is how the app asks for something without leaving the page — Filter & Sort, New goal,
New target, Add a resource, Attach a resource. It is a **drawer from the bottom on a phone** and a
**side panel from the right on a laptop**, with the *same card* inside either, and **the web and
Android draw the same card**. When the two disagree it is a defect, not a platform difference.

The parts, and what each is made of:

| Part | Web | Android |
|---|---|---|
| **Bottom drawer** | `DrawerContent` (`src/components/ui/drawer.tsx`): pinned to the bottom edge, full width, top corners **`rounded-t-xl` (12px)**, white, `overflow-hidden`, **no border**, **no grab handle**. Height is the caller's: `92vh` for the create forms, `max-h-[92vh]` for the filter panel, `100svh` + `rounded-none` for the full-screen note editor | `ModalBottomSheet`: `containerColor = Color.White`, **`dragHandle = null`**, `shape = RoundedCornerShape(topStart = SpiraRadii.lg, topEnd = SpiraRadii.lg)` (**12dp**), `skipPartiallyExpanded = true` |
| **Side panel** | `SheetContent side="right"` — `p-0 flex flex-col bg-white`, **`closeButton={false}`**, `sm:max-w-[420px]` for the filter panel, `sm:max-w-lg` / `xl` for the forms | — (phone only) |
| **Head** | `SheetHead` (`src/components/spira/SheetHead.tsx`) | `SpiraSheetHead` (`ui/components/SpiraSheetHead.kt`) |
| **Body** | its own scroller — `px-5 pt-4 pb-8 space-y-6 overflow-y-auto flex-1 min-h-0` | its own `verticalScroll` — `padding(horizontal = 20.dp, vertical = 16.dp)`, `spacedBy(16.dp)` |
| **Foot** | a pinned row of two: quiet outline left, filled Kale right, each `h-12 rounded-md flex-1`; `px-5 pt-3`, bottom `max(env(safe-area-inset-bottom), 12px)` | a `Row` of two `SpiraButton`s, Ghost then filled, `spacedBy(12.dp)`, 20dp sides, 24dp bottom |

**A sheet wears one of exactly two heads, and one component per surface draws both — neither may
grow a third** (owner, 2026-09-03, on the AI providers panel wearing the wrong one: "должно быть
2 типа drawers с kael шапкой — это основной вид, и с белой шапкой — это вспомогательный вид" —
"there should be 2 types of drawers: with a Kale head — the primary kind, and with a white head —
the auxiliary kind"). `Primary` is the band this section used to call "the" head, unqualified —
it still is, for every sheet that IS the reason the user opened it: a create/edit form, Filter &
Sort, the coach itself. `Auxiliary` is for a sheet that sits ON TOP of primary content without
being that content itself — the first, and so far only, case is **AI providers** ("это не
основной контент" — "it is not primary content" — reached from *inside* the coach panel to
manage keys, not itself the thing the user came here to work on).

Picking the wrong one is a defect the same way an off-palette colour is: never reach for
`Auxiliary` because a sheet happens to be short, plain, or nested inside another — reach for it
only when the sheet is genuinely secondary to whatever is already open behind it.

| | `Primary` | `Auxiliary` |
|---|---|---|
| Fill | `bg-primary` / `colorScheme.primary` — Kale-500 `#0A8080` | white |
| Bottom edge | none | `1px` hairline — `#F3F3F3` web, `Salt300 #F4F4F3` Android |
| Padding | 20 start, 12 end, 14 top and bottom | same |
| Title | white, **bold, 16px** (`titleMedium`), sentence case, left | Salt-1000 (`#003737` web / `#222525` Android), same weight/size |
| Close | a white X, 32px hit area, `hover:bg-white/15` | a muted-ink X, `text-[#003737]/40`, `hover:bg-[#003737]/5` |
| `actions` | anything about the sheet as a whole, **before** the X — today only the filter panel's padlock (see 7), which is `Primary` | same slot; nothing uses it yet |

Web: `SheetHead`'s `tone` prop — `"primary"` (default) / `"auxiliary"` —
`src/components/spira/SheetHead.tsx`. Android: `SpiraSheetHead`'s `tone: SheetHeadTone` —
`Primary` (default) / `Auxiliary` — `ui/components/SpiraSheetHead.kt`. **AI providers is
`Auxiliary` on both** (`ProviderSheet`, web and Android) — it opens from a strip inside the
coach's own chat, manages account-level keys that outlive any one conversation, and was never the
primary reason the panel is open.

**On a laptop, AI providers is also the one sheet that overlays its OWN parent rather than the
page.** Every other web sheet either portals to the page (a form, Filter & Sort) or IS the page
(the coach panel itself, docked on the left of the page as of 2026-09-03 — see the note in
`AppShell.tsx`; it has already moved sides once and may again, so don't hard-code "right" from
memory); AI providers stays nested `absolute inset-0` inside the coach panel and slides in from
the panel's own edge, not the page's — matching the fact
that it covers the *coach*, not the goal underneath it (owner, 2026-09-03: "на вебе это должна
быть обычная боковая панель поверх панели чата" — "on web this should be an ordinary side panel
OVER the chat panel". It had been hard-coded to the phone's bottom-drawer shape at every width,
which read as a drawer stuck inside a page that otherwise has none — every other sheet in the app
already splits bottom-drawer-on-a-phone / side-panel-on-a-laptop, so a sheet fixed to the phone
shape regardless of width was the actual defect, not a deliberate exception). On a phone it stays
the ordinary bottom drawer, `sheet-inset` sized like any sheet stacked inside another.

The rules that hold it together:

- **Create and edit forms wear the band too.** "New goal", "New target" and "Add a resource" used
  to have a white head with near-black type — on Android with Material's grey **drag handle** above
  it — so the same phone showed "Filter & Sort" in one design language and "New goal" in another
  (owner, 2026-08-22). A form sheet is not a different kind of thing from a filter sheet.
- **No grab handle, on either surface, ever.** On Android it would sit on the teal as a grey
  smudge; on the web vaul drew it on a **white strip above the band**, which is exactly what made
  the phone and the laptop look like two apps. The X closes the sheet and the drag and back
  gestures still work — the head itself is the drag surface.
- **No hairline on the drawer either.** Over the dimmed page it drew a pale outline round the teal
  head that the Android sheet has not got.
- **A side panel passes `closeButton={false}`.** The corner X would land on top of the head's own.
- **One gutter, 20.** The head, the fields and the footer buttons all start at the same x — 20px on
  the web (`px-5`), 20dp on Android. The forms used to indent their body 24px past their own title.
- **The wording is one string on both surfaces.** Android said "New resource" while the web said
  "Add a resource"; the web wins, as always.
- **Checking it by eye needs the card, not the sheet.** A modal sheet renders in its own window, so
  render `SpiraFormSheetContent` / `SpiraFilterSheetContent` directly — see 4 and the note in 7.
- Two web sheets still wear a white head that **predates the `tone` system and is not the
  `Auxiliary` kind** — both are primary content (a create/edit action, not a secondary panel over
  something else) and are to be converted to `Primary` when next touched, not left as they are on
  the theory that they're already white: the note editor's **Add a link** sheet (its head carries
  a description line that has to move into the body first) and the numeric **Update Progress**
  panel in `Targets.tsx`.

#### 3e-bis. A sheet's top edge is a constant (hard rule, 2026-08-29)

**No sheet is a percentage of the viewport.** Every one takes its height from a `.sheet-*` utility
in `src/styles.css`, and each is `calc(100dvh - var(--sheet-top-gap))`: the bottom is the
viewport's bottom, the top is a **constant** below its top. The on-screen keyboard shrinks a sheet
from below and its top edge does not move at all.

| Class | For |
|---|---|
| `sheet-h` | a sheet with no natural content height — the chat, the PDF preview |
| `sheet-max` | everything else: sized by its content, up to that cap |
| `sheet-inset` | a sheet stacked INSIDE another one — a percentage of its PARENT, so it cannot outgrow the sheet it sits on (the AI key sheet) |

**The gap is 76px**: the app header is a 64px sticky bar, and 12px of page shows below it so the
sheet reads as a sheet over a page. Android's `HEADER_CLEARANCE` (`AiChatHost.kt`) is the same
idea and leaves the same 11–12dp; it also clears the GROW tab row, because there the tabs are part
of the fixed top chrome, while on the web that strip is sticky page content the sheet may cover.

**Three earlier shapes failed, all for one reason** — a percentage of a viewport the keyboard
resizes cannot hold still:

| Written as | What the keyboard did to it |
|---|---|
| `h-[92vh]` | 92 % of what the keyboard left — a third of the screen |
| `min(92 * --app-vh, 100dvh)` | the cap won at 100 %: the sheet's top edge landed on the screen's |
| `min(92 * --app-vh, 92dvh)` | 8 % of 888 is 71 px, 8 % of 430 is 34 — a 37 px jump up, onto the app header ("подскакивает немного и начинает перекрывать хедер") |

`--app-vh` and `src/lib/spira/sheet-height.ts` were retired with the last of them. Do not bring
them back: they existed to make a *percentage* stable, and the percentage is the thing that never
worked. That module also cost a second bug on its own — it republished on `focusout`, the instant
the keyboard *starts* leaving while it is still there, which shrank every sheet mid-tap and ate
the first tap on any control in a sheet (BUG-064).

**What pays for a sheet not filling the screen is scrolling, and it is a chat rule, not a form
rule:**

- **The chat's footer floats over its transcript.** The composer's white card sits on the gradient
  with the conversation scrolling underneath it, so the last message can be scrolled clear of it.
  Two layers (`AiPanel.tsx`): an outer `absolute inset-0 pointer-events-none flex flex-col
  justify-end`, which has a definite height so `max-h-[70%]` resolves against it and lets touches
  through where the footer is not drawn; and an inner layer that hugs its content, carries that
  70 % cap for the whole stack, and is what `useFooterHeight` measures into the transcript's
  `padding-bottom`.
- **Everything that stands in the composer's place floats the same way and on the same ground** —
  a pending proposal card, the "Revising…" row, "Finish session", "Session complete", **and the
  composer itself**. None of them gets a slab of its own colour behind it; the gradient is painted
  once, on the box that holds the transcript and the footer together, and everything on it is
  transparent (owner, 2026-08-29: "карточка лежит на каком-то отдельном фоне, что неверно";
  "поле для ввода … на прозрачном фоне, а не плотном фоне").
- **Android does all of this too**, and the same way: one `Box` with the gradient, the `LazyColumn`
  filling it, and the footer `align(BottomCenter)` with `onSizeChanged` feeding the list's bottom
  `contentPadding`. Its keyboard scroll keys on `WindowInsets.isImeVisible`, and is instant for the
  same reason the web's is.
- **On Android the end of the conversation is not the top of its last message** (BUG-066). The web
  scrolls the transcript to `top: 99999`; a `LazyColumn` has no such coordinate, and
  `scrollToItem(lastIndex)` puts that item's TOP at the top of the viewport. The two are the same
  place only because a list cannot scroll past its own end — at the composer's resting height
  there is barely any padding below the last message, so the call **clamps** and lands at the end
  by accident. The keyboard removes the accident: its height becomes the transcript's bottom
  `contentPadding`, the clamp stops biting, and a reply taller than the panel is parked on its
  first line with the rest of it behind the composer, which is what the owner photographed
  (2026-08-29: "автопрокрутка чата не работает"). `scrollToConversationEnd` walks on from the item
  a viewport at a time until `scrollBy` consumes nothing, and **both** scroll effects — the
  streaming follow and the keyboard — go through it; the streaming one had no follow-through at
  all and, re-firing on every chunk, had the last word.
  `ChatScrollsToTheEndTest` pins it, and it has to **dispatch an IME inset by hand** onto Compose's
  own view: without a keyboard the test renders the accidental clamp and passes against the defect.
- **"Session complete" IS the input.** It renders in the footer and the composer stands down for
  it, so a finished session leaves nothing to type into — it used to be a message in the
  transcript with the composer still live underneath.
- **Opening the keyboard scrolls the transcript to the end** (`useKeyboardStickyBottom`), and
  **instantly**. The signal is a viewport that *shrank* while something typeable is focused;
  `focus` alone is a frame too early and would scroll against the old layout. Instant, not smooth,
  because the keyboard's own slide is already the motion and a 300 ms smooth scroll running
  against it — over a scroll height the footer's `ResizeObserver` is changing in the same frames —
  is three animations arguing. Growing back is deliberately not handled: the keyboard leaving must
  not yank a transcript the reader has scrolled up into.
- **A transcript resting at its end stays there** when the footer changes height (the composer
  grows a line, a card arrives), and only then — scrolling back through history is never yanked.
- **A form sheet keeps its solid pinned foot.** Cancel and Create are decisions, not a composer;
  content sliding behind them would be wrong. The browser already scrolls a focused field into
  view inside the body scroller, which is all a form needs.

**Nothing but these utilities may set a sheet's geometry**, and three things have tried:

- **`vaul` must never reposition inputs.** `src/components/ui/drawer.tsx` passes
  `repositionInputs={false}` to `Drawer.Root` (BUG-060). vaul turns it on by default and it writes
  an **inline `height` and `bottom`** onto the drawer whenever the visual viewport resizes with
  something typeable focused — an inline style beats every class, so with it on the sheet is not
  sized by `.sheet-*` at all, and four consecutive CSS fixes were no-ops. Its number is
  `initialDrawerHeight`, captured on the first resize the handler ever sees and reapplied for the
  drawer's life: 718 px at rest, 300 with the keyboard up, and **still 300 on a 780 px screen**
  once the keyboard went. The flag exists for browsers where the keyboard does not resize the
  layout viewport; `interactive-widget=resizes-content` means Chrome does, so vaul is a second
  hand on the same wheel. Everything else it guards is iOS-only — see
  `backlog/ios-safari-keyboard-covers-the-sheet-composer.md`.
- **A sheet is `overflow-clip`, never `overflow-hidden`, and it has exactly one scroller: its
  body** (BUG-063). The two clip identically, but `hidden` still makes the box a **scroll
  container** — it only hides the scrollbars — so anything that scrolls programmatically can slide
  the whole sheet inside its own frame. Something did: opening the deadline calendar called
  `scrollIntoView` on the field, and `scrollIntoView` walks up and scrolls **every** scrollable
  ancestor. The sheet landed at `scrollTop: 450` with its teal head at **y −292**. `clip` is not a
  scroll container at all.
- **A popover never states a height either.** `PopoverContent` caps itself at
  `max-h-(--radix-popover-content-available-height)` with `collisionPadding={8}`. Radix flips a
  popover above its trigger when there is no room below, but it will not **shrink** one that fits
  neither way — it hangs off the top. When the cap bites it must be the **content** that scrolls,
  not the card, so the deadline card is a flex column and its head and Today / Clear row stay put.

**An error is not a message** (owner, 2026-08-29: "и ошибки это не сообщения"). It goes to the
panel's notice and nowhere else — it used to be written into the transcript as a turn **and**
raised as a notice, so a provider's quota error appeared twice on one screen in two shapes. The
failed turn's empty streaming bubble is removed rather than filled in. An **error** notice does not
time itself out either, since it is now the only place the failure is reported; everything else
still clears itself after `PANEL_NOTICE_MS`.

**Nothing overflows its block.** A provider error is one unbroken URL
(`generativelanguage.googleapis.com/generate_content_free_tier_requests`) and it ran straight past
the right edge of both the notice and the chat's warning card. Two separate causes, and both are
needed: **`min-w-0`** so a flex item may shrink below its content's width at all, and
**`break-words` + `overflow-wrap: anywhere`** so the text then has somewhere to break. Either alone
does nothing.

**A sheet must not resize at the moment of a tap** (BUG-064). A sheet is `position: fixed; bottom:
0`, so shrinking it moves everything inside it down; when that lands between `mousedown` and
`mouseup`, Chrome dispatches **no `click` at all** and the tap is silently lost.

`e2e/sheet-chrome-stays-put.spec.ts` and `e2e/ai-drawer-height.spec.ts` hold all of it, and every
case was checked red against the code it guards — the top edge jumping 37 px, the floating footer
at a transcript ending 112 px above the composer, the keyboard scroll 1042 px from the end, the
swallowed tap. Note what it takes to see the last one: a **real touch** (`hasTouch`,
`page.touchscreen.tap`). Playwright's synthetic `.click()` does not go through the
mousedown/mouseup target comparison, so it cannot reproduce a swallowed tap.

#### 3e-ter. Asking for a date is a MODAL on a phone, a popover on a laptop (hard spec, 2026-08-29)

`DeadlinePopover` (web) and `DeadlinePickerDialog` (Android) are the app's one date control, and
they draw the **same card**. On a phone it went through two wrong shapes first, and both are worth
knowing:

- a **popover** — a floating card over an open form, its own teal strip directly under the sheet's
  teal band: two heads stacked for one question;
- a **bottom sheet**, which fixed that and introduced another. A sheet is how the app asks for
  something *about the page*, and it comes from the bottom edge because it belongs to what is
  under it. A calendar is a **value being picked inside a form that is already open**, and a
  second bottom sheet stacked on the first reads as leaving that form.

So it is a centred modal, which is what Android had all along.

| | Laptop — popover | Phone / Android — modal |
|---|---|---|
| Chrome | its own compact teal strip | the shared `SheetHead` / `SpiraSheetHead` |
| Commit | tapping a day commits and closes | a day is a **draft**; the foot commits |
| Foot | `Today` / `Clear` as small links | `Cancel` (quiet outline) · `Set deadline` (filled Kale) |
| Cells | `--cell-size: 2rem`, a mouse target | finger-sized and full width |
| `Today` / `Clear` | commit immediately | set the draft, like everything else in the card |

Four things that are the spec, not implementation detail:

- **The phone confirms; the laptop commits.** On a finger-sized date grid a mis-tap that commits
  *and closes* leaves nothing to undo. `Clear` is a draft too, and the confirm word follows it —
  `Remove deadline` when there is one to remove and the draft has dropped it, `Set deadline`
  otherwise, disabled when there is nothing to confirm.
- **The head states the draft** (owner, 2026-08-29): the chosen date once there is one, and
  "Set deadline" only while there is none — including straight after `Clear`, where reverting to
  the prompt is exactly what is about to be true. It was a line of its own above the grid, which
  said the same thing twice: a band with a fixed title, then a sentence restating what the band
  was for. The format is **`MMMM d, yyyy` and nothing else**, which is what the laptop's popover
  head has always shown, so all three surfaces read identically. The weekday and the
  "13d overdue" are dropped on purpose — with them the title is ~250px, which fits a 412px phone
  and truncates on a 320px one, and a head that is sometimes cut is worse than one that is always
  short. Both still show on the trigger the picker was opened from.
- **ISO week numbers, on both surfaces**, in a narrow `W` column down the left. The web gets them
  from `react-day-picker` (`showWeekNumber` + `ISOWeek`); Android draws its own grid
  (`SpiraMonthGrid`) because **Material 3's `DatePicker` has no week-number support at all** — and
  a bare Material picker in a Spira form was a raw platform default besides (see 1). Use
  `WeekFields.ISO`, never `WeekFields.of(Locale)`: the locale form starts weeks on Sunday in the
  US and numbers the same rows differently, so the two surfaces would disagree. Six rows always,
  so the card does not change height as you page through.
- **The trigger is described once and rendered twice** on the web. Seven variants (`pill`,
  `input`, `button`, `text`, `icon`, `icon-text`, `renderTrigger`) each have to be a
  `PopoverTrigger` on one surface and a plain button on the other; `DeadlinePopover` builds
  `{ className, content }` and picks the element afterwards, so the two cannot drift.

The web switch is `useIsMobile()` (768), matching every form sheet; a modal is the right shape on
both sides of the 640/767 seam `ListToolbar.tsx` documents, so unlike a sheet it raises no
question about which container opened it. The modal's only stated height is `.modal-max`
(`calc(100dvh - 32px)`): a centred card has no top edge to hold still, it simply must not outgrow
the screen when the keyboard is up, and then the **grid** is what scrolls.

`SheetHead` takes a `titleComponent` so a dialog can pass `DialogTitle` — Radix needs one for the
dialog's accessible name, and a second screen-reader-only copy beside the band would announce the
title twice. One head, one heading.

`e2e/deadline-picker.spec.ts` pins both surfaces, the laptop's one-click commit included;
`VisualCheckDatePickerTest` pins Android's week numbers against a known month (August 2026 runs
31–36) and writes the picture.

#### 3f. Search fields — the reset is the word "Clear", never a cross (hard spec, 2026-08-22)

Every search field in the app — the All-goals header, the goal-workspace switcher, the Will do /
Resources / Options toolbars, the resource pickers — empties itself through **the word `Clear`**
set inside the field on its right, in **Kale**, semibold, sentence case. It appears only while
something is typed.

The reason is the phone. An open search on a narrow screen is a field **plus a cross that closes
it**, so a cross *inside* the field put two identical marks a few pixels apart, and neither of them
said which one dropped the query and which one dismissed the search (owner, 2026-08-22). A word
cannot be mistaken for the button beside it, and the two jobs stop looking like one control drawn
twice.

One component per surface draws it: **`ClearSearchWord`** in `src/components/spira/ListToolbar.tsx`
(web — the section fields and the header's own field all use it) and the `Clear` text inside
**`SpiraSearchField`** (`ui/components/GoalWorkspaceChrome.kt`, the one search input on Android).
Never hand-roll a clear control, and never put an X back inside a field.

The X that **closes** a search stays exactly as it is — the white disc beside the field on Android,
the corner button on the web. It is the one cross in the row.

#### 3e-quater. Two things a headless host cannot see (hard rule, 2026-08-29)

Robolectric and a headless browser render for real, and both are blind in the same place: **what
the platform does with a window**. Two defects were shipped behind that blindness in one session,
and in each case an assertion was written against the defect and **passed with the fix reverted**.

- **A Compose `Dialog` defaults to `usePlatformDefaultWidth = true`** — the platform picks the
  width, ~320dp on the owner's phone, and the date picker's foot wrapped "Set deadline" onto two
  lines. Robolectric's dialog window is the full screen width whichever way the flag is set, so a
  rendered-width assertion cannot tell them apart. A modal that states its own size must pass
  `usePlatformDefaultWidth = false`.
- **The on-screen keyboard**, already documented in 3e-bis.

Where a rendering assertion cannot bite, check the **source**, the way `sheet-units.test.ts` checks
vaul's `repositionInputs`, `DeadlinePickerDialogTest` checks this flag and `ChatFooterConventionTest`
checks a modifier order. Three ways a source scan passes while the defect is present, all three hit
in one session — verify a source check **red** before trusting it:

1. **It reads its own explanation.** The KDoc above the call named `usePlatformDefaultWidth = false`
   twice, so the scan found it with the code reverted. Strip comment lines before matching.
2. **"A before B" searched the whole file.** `indexOf(".imePadding()", fromIndex)` found the *next*
   one further down — the composer has its own — so a reversed order still passed. Give the check
   the single block both are supposed to be in.
3. **The block was sliced on the wrong token.** Cutting at the first `") {"` ended the chain inside
   `with(density) {`, and the test then reported the keyboard padding *missing* rather than
   mis-ordered — a scan can fail in a way that looks like the thing it is looking for. Take the
   lines, ending on the one that is exactly `) {`.

**`onSizeChanged` goes above the padding it must include.** Modifiers apply outside-in and each
padding reports the *padded* size upward, so a measurement placed after `imePadding()` /
`navigationBarsPadding()` misses the nav bar and the keyboard. That is what left the chat's
transcript padded too short to scroll to its end.

**The Android app IS testable end-to-end, without a login — use it.** This was written off twice
as "sign-in is interactive, only the owner can do it", which is true of *production* and irrelevant
locally: the backend's `local` profile already signs **every** request in as `dev@local`
(`LocalDevAuthFilter`), and the app's backend URL is a build-type default. Nothing had to be added.

**Use the `dev` build type for every local check — never `installDebug`** (owner, 2026-08-30).

`dev` is the Android twin of the backend's `local` Spring profile, and it works the same way,
because **the login is not the app's decision**: the app asks `/api/auth/me` on start and believes
the answer. It has no bypass of its own and must never grow one.

| Build type | `BuildConfig.API_BASE_URL` | Sign-in | Cleartext HTTP |
|---|---|---|---|
| **`dev`** | `http://10.0.2.2:8080` — the host, from inside the emulator | **none**, when that backend runs the `local` profile | yes |
| **`debug`** | production (Cloud Run) | real Google — which only the owner can complete | yes |
| **`release`** | production (Cloud Run) | real Google | no |

`debug` points at production on purpose: it is what `distributeDebug` sends to the owner's phone,
so a build made without thinking about it is the safe one. That is also why an agent must not
reach for it locally — it lands on a sign-in screen it cannot get past, which is exactly the dead
end that had this written off twice as "only the owner can do it".

`-PspiraApiBaseUrl=…` overrides either, for what neither default covers: a real phone on the LAN
(`http://<PC-LAN-IP>:8080`) or a cloudflared tunnel URL. Full reference, both surfaces, in
`README.md` → "Build variants — skip Google login for quick local checks".

```
# 1. backend on the local profile (Docker Postgres first) — see Build / run reference
# 2. an emulator; the windowed mode crashes on this machine's GPU, headless does not
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd spira_pixel     -no-window -no-snapshot-load -gpu swiftshader_indirect -no-boot-anim -no-audio
# 3. the app — `installDev` is the build type pointed at the host from inside the emulator
cd android && ./gradlew.bat :app:installDev
adb shell settings put secure show_ime_with_hard_keyboard 1   # a REAL soft keyboard
adb shell settings put global hide_error_dialogs 1            # see below
```

It lands on **All goals** with the local database's real data — confirmed on the emulator,
2026-08-30. `adb shell input tap/swipe/text` drives it and `adb exec-out screencap -p > shot.png`
photographs it, which is how the chat's keyboard scroll was finally confirmed after two rounds of
shipping it unverified. Three independent signals that the dev build is really what is running:
`curl http://localhost:8080/api/auth/me` answers `dev@local` rather than 401, `adb shell dumpsys
package com.spiramindscape.android | grep versionName` ends in **`-dev`**, and the screenshot has
no sign-in screen on it.

Three things that make the difference between this working and looking broken:

- **`hide_error_dialogs 1`.** Under software rendering SystemUI ANRs constantly, and its dialog
  sits on top of everything and swallows every tap. Killing or dismissing it does not help — it
  comes straight back. This setting stops it being drawn at all; the app underneath was fine the
  whole time.
- **`show_ime_with_hard_keyboard 1`**, or the emulator uses the host keyboard and there is no IME
  to open — which is the entire thing being tested.
- **A conversation to scroll.** `PUT /api/ai/chat/transcript` seeds one with no model calls and no
  cost. **It overwrites whatever is there**, and under the `local` profile that is the same
  `dev@local` transcript the tunnel uses — read it first if it might matter.

**`installDev` and `distributeDebug` are different build types, so they cannot be confused any
more.** They used to be one — `installDebug -PspiraApiBaseUrl=…` — and the flag is baked into
`BuildConfig` at assemble time, so distributing after a local run sent the owner a build pointing
at `10.0.2.2`. `distributeDebug` now **fails** if that flag is set at all.

#### 4. Verify UI changes visually before shipping

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

#### 5. Menus & overlays are pure white

**All dropdowns, menus, popovers, and overlay surfaces have a plain white background** — no
tint. On Android this means clearing Material's tonal-elevation overlay (`surfaceTint =
Color.Transparent` in the theme) so menus don't pick up a teal cast; on the web, don't let a
popover inherit a tinted/elevated background. If a menu looks greenish/grey, it's wrong — fix the
surface, don't ship it.

#### 6. Dropdown / menu anatomy (hard spec — don't reinvent)

There is **exactly one** menu surface on Android: `ui/components/SpiraDropdownMenu.kt`
(`SpiraDropdownMenu` + `SpiraMenuItem` + `SpiraMenuDivider`). **Never** use Material's
`DropdownMenu` / `DropdownMenuItem` in product UI, and never hand-roll a one-off menu — Material's
default reads as a flat grey rectangle and was explicitly rejected. Every kebab (⋮) menu and action
menu uses `SpiraDropdownMenu`. If it can't express what you need, **extend that file**, don't fork
it.

> **Not sort and filter, though** — those left the menus entirely on 2026-08-21 and open a panel
> instead; see 7. What is left here is the per-element ⋯ menu and the action menus.

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

#### 7. Sort and filter chrome (hard spec, 2026-08-21)

**A filter is never a dropdown.** One panel holds every question a list asks — a **drawer from the
bottom on a phone, a side panel from the right on a laptop**, and the *same tree of questions*
inside either. Both surfaces implement it once — Android `ui/components/SpiraFilterSheet.kt`, web
`src/components/spira/ListToolbar.tsx` (`ToolbarSheet`) — and neither may grow its own variant.

> This supersedes the 2026-08-13 spec, which had a dropdown of columns on wide screens and a drawer
> only on phones. The web's menu parts (`ToolbarTrigger`, `MenuColumns`, `MenuGroup`, `MenuChoice`,
> `ToolbarMenu`) are **deleted**, so there is nothing left to build a filter menu out of. Don't
> reintroduce them.

- **The trigger is the filter glyph alone**, in **Kale**, no border, no fill, no pill and no
  chevron, carrying a **Guava dot** when anything is narrowing the list — at **every** width. It is
  teal in every state, *including when nothing is chosen*: the old near-black chip made an untouched
  toolbar the heaviest row on the page. Android's `SpiraFilterSortTrigger` is the same mark.
- **The dot goes ON the glyph.** A trigger that carries a word shows the count in brackets instead —
  "Filter (2)" — and never both, because the word and the number already say a filter is on.
- **The panel** has a **Kale head**, the questions under small uppercase headings, and **Reset all**
  beside **Apply** at the foot. The confirm word is **Apply**, never "Done", and **Reset all is
  always present**, not only once something is on.
- **Reset all resets ALL** (owner, 2026-08-21). There used to be a class of "standing preference" it
  was not allowed to undo — the status questions — so a button promising everything quietly kept
  four answers. There is no such class now: one `DEFAULTS` table per list, and reset restores it.
- **A goal's lists answer for themselves — filters, sort AND padlock** (owner, 2026-08-31).
  `targets`, `options` and `resources` live *inside* a goal, so each goal keeps its own answers and
  its own padlock; only the All-goals list is app-wide. Both stores key on the goal id — the web by
  scope (`targets:<goalId>`), Android by prefixing **every** key it touches, the padlock included.
  - **Two faults, and the second is the worse one.** One flat set of answers meant a filter pinned
    on goal 1 was already on when goal 2 opened *with goal 2's padlock open* — and then changing it
    there rewrote goal 1's arrangement behind goal 1's *closed* padlock. A lock another screen can
    write through is not a lock.
  - **A goal-less caller gets `NO_GOAL`, on both surfaces** — and that is a **legibility** guard,
    not an isolation one. No goal has a blank id, so a blank one could never collide with a real
    goal's answers whatever the fallback. What the name buys is that `targets:none` (web) and
    `none.filter` (Android) say plainly "something wrote without a goal", where a bare `targets:`
    reads as the shared bucket this scoping removes and a key starting with `.` reads as
    corruption. Android normalises it **in the constructor**, so a store built directly cannot skip
    it. The app-wide store takes `APP_SCOPE`.
  - **A goal-owned list's pre-scoping arrangement is dropped** — one global answer cannot honestly
    be attributed to any single goal, so those lists open on their defaults with every padlock open.
  - **The All-goals list is the exception and migrates intact, padlock included.** It was never part
    of the defect: it is app-wide, there is only one of it, and its old keys mean exactly what they
    mean now. Dropping a dashboard arrangement the user had pinned would be the failure this very
    spec names, inflicted by the fix for a different list. Web: `migrate` lifts the flat fields into
    `views.goals`. Android: the `schema` bump **renames** `spira_goal_view`'s keys into `app.*`
    (`adoptLegacyKeys`) instead of clearing them. `viewMode` survives on the web too — it is not a
    filter and no padlock covers it.
  - **Known gap:** a deleted goal's scope is never pruned, on either surface. It is small and
    bounded by the 50-goal cap for live goals, but deleted ones accumulate — see BUG-067.
- **A padlock sits in the head, right of the title, beside the X** (owner, 2026-08-21) — one per
  list **per goal**, on both surfaces.

  | Padlock | What it means |
  |---|---|
  | **Closed** | This list's filters and sort are written down and come back after a reload, a closed tab and an app restart. Every later change is written too, so the lock holds **what is on screen**, not a snapshot of when it was shut. |
  | **Open** | Nothing is written and whatever was written is **cleared**, so the list opens on its defaults. Opening the padlock is therefore the whole "forget" gesture — there is no separate clear step. |

  Web: `shell-store.ts` (`locked`, `setLocked`, `partialize`). Android: `ViewPreferences.kt`
  (`LockablePreferences`). **Unlocking is not a reset** — what is on screen stays; only the memory
  goes.

- **A closed padlock pins EVERY question the panel asks, the deadline range included** (owner,
  2026-08-22). The range was exempt for a day, on the argument that a range is about a moment; then
  the owner set one, shut the padlock, left the app and came back to an empty field with the
  padlock still closed. **A control that promises to keep the arrangement and silently drops two of
  the answers is the worse failure** — nothing on screen admitted the range was not covered. The
  original worry is answered by two things that shipped alongside it: a list emptied by its own
  filter says so in a warning notice, and the trigger carries a Guava dot. A restored range is not
  invisible.
- **Never pinned, padlock or no padlock**: the **search box** alone — a query belongs to the screen
  it was typed on.
- **A question's shape says what kind of question it is**: filter values are **pills** (see 3c), a
  *modifier* — Ascending / Descending — is a **segmented control**, and a **sort key** is a
  **choice card**. Three shapes, so a glance tells them apart without reading.
- **"No deadline" and a date range can never both be on**, on any list. A range asks which deadlines
  to keep and "No deadline" asks for the rows that haven't got one, so together they match nothing
  and neither control says why. Picking either takes the other off.
- **The target filter asks five independent questions**, and a target must pass all of them:
  **Progress** (All / Done / Not done) · **Status** (Started / Not started) · **Deadline** (All /
  Overdue / Not overdue / No deadline) · **Type** (All / Done-not-done / Numeric / Checklist) ·
  **Lock** (All / Locked / Unlocked), then the deadline range. Done-ness is **Progress** and
  started-ness is **Status** (owner, 2026-08-18): finishing is the far end of a progress bar, while
  having begun is a state the target is in. "Overdue" follows the card's own rule — past *and* not
  yet achieved — so a target finished late is not listed as overdue. There is **no "achieved
  between" range**; it was the web's alone and came off both surfaces on 2026-08-20.
- **Resources are filtered by Type** (All / Notes / Links / Files / **Emails**) and sorted by
  Created or Name. There is deliberately **no sort by type**: the type question is the filter's, and
  ordering by it only reshuffles cards the filter can simply hide. The word is **Emails**, never
  "Contacts" — the stored type is `email`.
- **Options has no sort** (position is the meaning of that list). Its toolbar is **Reorder on the
  left, then the lean filter**: All / Good idea / Bad idea / Didn't try, the first two carrying the
  smiley glyphs the card's badge uses.
- **Reorder is unavailable while a list is narrowed** by a search or a filter, on both surfaces. A
  drop sends the card's index in the *rendered* list as an absolute `position`, so on a filtered
  list the wrong order is saved with nothing on screen to say so.
- **A list emptied by its own search or filter is a `Warning` notice**, not a grey empty state — see
  3d. Web: `FilteredEmptyNotice` in `src/components/spira/Notice.tsx`.

> Verifying the panel on Android needs care: a `ModalBottomSheet` renders in **its own window**,
> which the `VisualCheck*` screenshot helper (it draws the activity's decor view) cannot capture —
> an open sheet is simply absent from the PNG. Render `SpiraFilterSheetContent` directly instead, as
> `VisualCheckFilterSheetTest` does, or the one check that would catch a question running off the
> side of the screen silently checks nothing.

---

### Brand

These are the Spira brand rules — typography, colour, and the usage "do / avoid" list. They apply
to **every** surface (web + Android). The Android tokens live in
`android/app/src/main/java/.../ui/theme/` (`Color.kt`, `Type.kt`) — change the token, not one-off
values.

#### Typography

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

> **The body face is switchable at runtime while the owner picks one** (GRO-122): **Settings →
> Fonts** carries **many** candidates at once — fifteen as of 2026-08-17, grouped by whether they
> have Cyrillic — and a tap re-fonts the whole app. It moves **only** the body face; headings stay
> ITC Clearface.
>
> **Adding one more is a routine five-file operation, and it is NOT the brand-swap procedure.**
> Both are written out in `docs/changing-fonts.md` — §1 to add a candidate, §3 to swap a brand face.
> Keep the web (`src/lib/spira/app-font.ts`) and Android (`ui/theme/AppFont.kt`) lists identical
> down to the wording, or the phone and the laptop stop being comparable, which is the whole point
> of the tab.

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

#### Font loading strategy

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

#### Colour

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
  - **The page is `#FFFFFF`, on both surfaces, and so are the cards** (owner, 2026-08-21). This is
    a measurement, not a mood: the web's `--background` / `--surface` / `--card` are all
    `oklch(1 0 0)` and Android's `SpiraBackground` is `White`. It had drifted on both — the web
    painted page *and* cards `oklch(0.982 0 0)` (`#F9F9F9`, a cold grey with no chroma at all),
    Android painted the page Parsnip-100 (`#FBFAFA`, warm), and the dashboard route then covered
    the lot with a hardcoded `bg-[#F4F4F3]/80` written straight into the markup. Nothing looked
    broken; the whole app just read grey.
  - **A card is told from the page by its hairline border, not by a different fill.** That is why
    they stay legible on white, and it is the thing to reach for before tinting anything. The one
    recessed tone left is `--surface-sunken` (table heads, wells).
  - **A page-level fill does not belong in a route's `className`.** If a surface needs a colour it
    comes from a token; `bg-[#…]` on a `min-h-screen` wrapper is how the grey survived a palette
    pass that was supposed to have removed it.
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
Salt-500, background = **White**, cards/menus = White, destructive =
Guava-600. (`success` is a functional green — new work should take it from the **success** ramp
above rather than picking a fresh green.)

#### The AI chat gradient (allowed)

The assistant's conversation area is painted with a two-stop vertical gradient — **teal `#83D2D2`
at the top fading to near-white `#F2FFFF` at the bottom** (owner, 2026-08-17). Both stops are
**allowed colours** wherever the AI panel needs them: the chat background
(`linear-gradient(180deg, #83D2D2 3.43%, #F2FFFF 118.85%)` on the web, the equivalent
`Brush.verticalGradient` on Android), the light composer area that continues the bottom stop, and
the provider status dot (connected = `#83D2D2`, missing key = `#F2FFFF`). The header stays dark teal
(web Kale-500, Android Kale-600). `#83D2D2` sits between Kale-300 and Brand-400 and `#F2FFFF` is an
off-white — they are the AI surface's own pair and are not a fourth progress-bar variant.

#### The gradient border (allowed)

A second gradient the owner has approved (2026-08-18): a **near-white card inside a warm-to-cool
diagonal gradient border**. It is one declaration — the fill is painted to the padding box and the
gradient to the border box, so the border is the only thing the gradient touches:

```css
background:
  linear-gradient(#fdfdfb, #fdfdfb) padding-box,
  linear-gradient(228.47deg, #ff4833 48.94%, #ed7ffe 89.8%, #7f8eff 143.09%) border-box;
```

The stops are `#FF4833` (a coral a shade hotter than Guava) → `#ED7FFE` (orchid) → `#7F8EFF`
(periwinkle), over the fill `#FDFDFB`. **Those five values are allowed only in this pairing** — the
gradient is a border treatment, not a palette. Do not fill a surface with any of them, do not pull
one stop out to tint type or an icon, and do not add a fifth progress-bar variant out of it.

On Android the same thing is a `Brush.linearGradient` painted into a rounded **border**
(`Modifier.border(width, brush, shape)`) over a `#FDFDFB` background — never a `background(brush)`,
which would fill the card instead of outlining it.

#### Progress bars — exactly four variants (hard rule)

**Every** progress bar/ring in the app (web + Android) — goal cards, target cards and strips,
numeric target bars, any linear or circular progress — must be **one of these four pairs**, track
then fill, and nothing else. No `#EA580C` (a Tailwind orange that is **not** in the palette and was
the old "in progress" colour), no grey `bg-secondary` / `surfaceSunken` track.

| Variant | Track (unfilled) | Fill (progress) |
|---|---|---|
| **Guava** — a target/goal still *in progress* | Reserved-200 `#FFEDEA` | Guava-500 `#F45D48` |
| **Contrast** — coral on teal; the **All-goals** cards | Brand-200 `#E5F4F3` | Reserved-700 `#E4523E` |
| **Brand** — a lighter teal alternative | Brand-200 `#E5F4F3` | Brand-500 `#4CACAC` |
| **Kale** — a *done* bar, and the card strip | Kale-300 `#8DD3D4` | Kale-500 `#0A8080` |

The convention in use: a target/goal that is **in progress** uses **Guava**, and it flips to
**Kale** once **done** (100%). The card's thin **progress strip** is always **Kale**. Use **Brand**
only where a deliberately quieter teal progress is wanted.

**Contrast** (owner, 2026-08-17) is the pair on the **All-goals goal cards**: the two brand colours
against each other rather than two steps of one, so a bar reads at a glance down a long list. It is
the only variant whose track and fill come from *different* families — which is the point of it, and
also why it must not be mixed into the target card, where Guava→Kale already carries the done-ness.

Web: `ProgressBar.tsx` (`tone`) and the strips in `Targets.tsx`. Android: `CircularProgress` /
`SpiraLinearProgress` in `CoreComponents.kt` and the strips/bars in `TargetCard.kt`.

> The type params above (**leading / tracking / alignment**) are **font-independent** — they're set
> on the type scale (`Type.kt`) and per-usage alignment, so they hold no matter which heading font
> ships. Don't tie them to a specific font.

#### Goal-workspace navigation (Android)

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

#### AI proposal cards — the web is the spec, verbatim

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

**Two rules about ending a session, because both were learned by losing whole sessions** (owner,
2026-09-08 — full account in `docs/grow-sessions-rag-guide.md` §9a):

- **A close is three steps, never one turn**: the **record** (what this session was about, written
  for the next one), then the **proposals** as their own dedicated turn, then the **goodbye**. Asked
  for together, models write the record, say goodbye and skip the middle — so a session that agreed
  on something changed nothing on the goal. The record and the proposals are different things: one
  is memory for the coach, the other is edits to the user's goal.
- **"End" is local, unconditional and involves no AI at all.** It cancels the stream, stops the
  timer, clears the draft and leaves. It must never be an instruction sent to the model and must
  never wait for one — that is how a dead provider left the owner in a session with no exit. A
  session ended that way has **no record**, and the closing card says so plainly rather than
  offering to save an empty one over the last session's.

#### Options cards (interaction)

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

### Claude Design (the design tool)

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
   - Match the reference screenshots for visual fidelity (see Design → Components and chrome → 4: verify UI changes visually)

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

**Start the database first** — the backend will not boot without it, and that is the single most
common way a local run fails before it starts.

| Task | Command | Notes |
|---|---|---|
| **1. Database** | `docker compose -f backend/docker-compose.yml up -d postgres` | Needs Docker Desktop **running**, not merely installed. Check with `docker info`. |
| **2. Backend** | `cd backend && .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` | The `local` profile auto-logs-in `dev@local` — **no Google sign-in needed**. **`mvn` is NOT installed — always use `.\mvnw.cmd` (Windows) / `./mvnw` (bash)** |
| **3. Frontend** | `npm run dev` | Vite on `http://localhost:5173`, proxying `/graphql` and `/api` to `:8080` |
| Backend run (real Google login) | `cd backend && .\mvnw.cmd spring-boot:run` | Needs `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` |
| Frontend tests | `npm test` | Vitest |
| Frontend E2E | `npm run test:e2e` | Playwright, against the **running** stack. Chromium is already installed — no `playwright install` needed. |
| Frontend build | `npm run build` | |
| Backend tests | `cd backend && .\mvnw.cmd test` | ~866 tests |
| **Android run (local backend, no login)** | `cd android && .\gradlew.bat :app:installDev` | **The one to use for any local check.** Needs the backend on the `local` profile + an emulator. See Design → Components and chrome → 3e-quater, and `README.md` → "Build variants" |
| Android build | `cd android && .\gradlew.bat :app:assembleDebug` | The **production**-pointing build — what testers get. `assembleDev` is the local-backend twin |
| Android tests | `cd android && .\gradlew.bat :app:testDebugUnitTest` | ~19 min for the full sweep; prefer `--tests "*OneClass"` while iterating |
| **Note editor** (after editing `embeds/note-editor/`) | `npm run build:note-editor` | **Easy to forget and silent when you do.** The Android note editor is that TypeScript bundled into `android/app/src/main/assets/note-editor/index.html`; without this step the app keeps running the old asset and your change simply is not there. |
| Android distribute (APK → email link) | `cd android && .\gradlew.bat distributeDebug -PreleaseNotes="what changed"` | Builds the debug APK and uploads it to Firebase App Distribution; testers (incl. the owner) get an email link. Uses your `firebase login`. |
| Public URL for phone testing | `.\tunnel-start.ps1 -Build` | cloudflared quick tunnel over the **built bundle**. Reachable from any network, mobile data included. |

Full local run (DB + backend + frontend), mobile testing over a tunnel, and deploy details are in
`README.md`.

**About that tunnel, two things that are easy to get wrong:**

- **Always `-Build` unless you need hot reload.** The dev server sends uncompressed ES modules, one
  request per file: one cold page load measured **6.17 MB over 103 requests** against **1.37 MB over
  19** for the built bundle. That is what exhausted a 1 GB ngrok allowance in days, and why free
  relays return 502 mid-load. The script also has to start the tunnel **before** the server — Vite
  reads its host allow-list once at startup — which is the reason it exists rather than a sequence
  you type by hand.
- **A tunnel URL has no login on it.** Under the `local` profile `LocalDevAuthFilter` authenticates
  *every* request as `dev@local`; there is no sign-in screen. Anyone holding the link is inside the
  app with full access to the local database, real API keys included. A random hostname is
  obscurity, not authentication — say so plainly when handing a URL over, and do not post one
  anywhere public.

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

On Android that means **`:app:installDev`** against a backend on the `local` profile — the build
type that needs no sign-in (Design → Components and chrome → 3e-quater). `installDebug` points at
production and stops at a login the agent cannot complete, which is the whole reason this used to
be handed back to the user.

**Only the user can do:** complete an interactive **Google sign-in** (a real account + consent
in the system UI) — which is needed for *production* only — and any action in the **Google Cloud /
Firebase web consoles** (creating OAuth clients, Firebase projects, secrets). The agent has no
browser access to those and cannot tap on a physical device.

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
