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

Full local run (DB + backend + frontend), ngrok mobile testing, and deploy details are in
`README.md`.

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
