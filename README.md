# Spira

A full-stack goal-tracking application built as a personal learning project. Spira helps people
set, structure and track long-term personal goals using the **GROW** coaching model — Goal,
Reality, Options, Will — on the web (desktop and phone) and in a native Android app, with an AI
coach that works inside the same model.

**🌐 Live:** https://spira-952567559986.europe-west1.run.app

> ⏳ **The first load can take 5–15 seconds — that is a cold start, not a bug.** The app runs on
> Cloud Run's free tier, which scales to zero when nobody is using it. The first visit after a
> quiet spell starts a container and boots Spring Boot; every request after that is instant until
> it goes idle again.

### Two ways to use Spira

- **Just use it / show someone** → open the live URL. Real **Google sign-in**, and each account
  sees only its own data.
- **Develop or test locally without signing in** → run the backend on the **`local` Spring
  profile**, which signs every request in as a throwaway `dev@local` user. See
  [Run it locally](#run-it-locally). Production never uses that profile.

---

## Why this project exists

I graduated from Jensen Yrkeshögskola in May 2026, specialising in systems development with a
focus on testing. This project is how I learn best — by building something real, making mistakes,
and figuring out why things work the way they do.

The idea came during my web application development course, when I wanted to understand exactly
how a backend and frontend connect and communicate. I rebuilt this project several times as my
understanding improved.

At some point, I learned that clear specs and tests are the best form of documentation:

- they verify behavior
- they catch regressions
- they make intent explicit for collaborators and AI tools

## The problem I was solving

In summer 2023 I moved to Sweden with one large goal: to build a life here. In practice that meant
many parallel goals — language, education, work, social integration. I needed one place to
structure all of it and track visible progress.

I first tried plain notes, then traditional task-management apps, and then goal-tracking products.
Most tools were still focused on work-style processes (OKRs, team performance, reporting), while I
needed a personal long-term goal system.

So I built Spira.

---

## What the application does

**All goals** — the home page: every goal as a card with its progress, confidence and deadline,
with search, a filter-and-sort panel (whose padlock keeps the arrangement across visits), and the
**Upcoming** column beside it.

**The goal workspace** — one page per goal, in the GROW order:

- **Goal** — title, description, confidence (1–10, with history), deadline.
- **Reality** — actions already taken and current obstacles.
- **Options** — possible ways forward: one active option, a "good idea / didn't work" mark on each,
  drag-to-reorder.
- **Will do (targets)** — measurable execution, in three kinds: done / not done, numeric
  (current of total, with a start value), and checklist. A target's progress can be **locked**.
  Goal progress is calculated from its targets, identically on the frontend and the backend.
- **Resources** — notes (a rich-text editor, exportable to Google Docs), links, files and email
  addresses, attachable inline to targets, options and reality items.

**Upcoming and reminders** — the All-goals column lists the nearest important dates and the
deadlines of goals, targets and checklist items. Each event can carry **reminders** ("at the time
of the event", "1 hour before", custom). When one goes off, the phone or desktop gets a **Web
Push** notification with Done / Snooze buttons, and Spira itself is covered by a full-screen
"decide now" page until it is answered. See [`docs/reminders-and-web-push.md`](docs/reminders-and-web-push.md).

**Calendar** — a month view of the same deadlines.

**AI coach** — a chat beside the goal that can read the goal and its resources (images included,
through a vision-capable model), search the web, and **propose** changes — new targets, edits,
an active option, notes. Nothing is applied until the user approves the proposal.
**GROW sessions** run the coach as a timed, structured session through the four phases, and each
one ends with a written record the next session picks up. See [AI coach](#ai-coach).

**Settings** — profile, notifications (whether this device can be reminded, plus a test
notification), fonts (the body face is switchable while one is being chosen), about, licences.

**Android** — a native Kotlin / Jetpack Compose app on the same backend and the same account.
See [Android app](#android-app-native) and [`MOBILE.md`](MOBILE.md).

Sign-in is **Google only**; there are no passwords, and every row of user data is owner-scoped.
See [Authentication and security](#authentication-and-security).

---

## Architecture in one paragraph

A single-origin web app: a **React SPA** served by the same **Spring Boot** container that exposes
a **GraphQL** API (`/graphql`) plus a few REST endpoints (`/api/...` for auth, AI streaming, push,
reminders), backed by **PostgreSQL**. Sessions are server-side, stored in PostgreSQL, so data and
login are shared by every surface — desktop web, phone web and the Android app, which reuses the
same API. Product direction is in [`specs/tech-stack.md`](specs/tech-stack.md) and the phased plan
in [`specs/roadmap.md`](specs/roadmap.md).

How one request travels:

1. The frontend sends a GraphQL POST from `src/lib/spira/api.ts` (state and optimistic sync in
   `src/lib/spira/store.ts`).
2. In development, Vite proxies `/graphql` and `/api` to `http://localhost:8080`
   (`vite.config.ts`). In production there is no proxy: one container, one origin.
3. `backend/.../graphql/SpiraGraphqlController.java` resolves it; services in the domain packages
   (`goal/`, `target/`, `resource/`, `upcoming/`) apply the rules, always scoped to the signed-in
   user.
4. Spring Data JPA reads and writes PostgreSQL; Flyway migrations shape the schema.

### Why GraphQL instead of REST

A goal page needs the goal and several nested lists at once. One GraphQL query asks for exactly
the fields that page shows:

```graphql
query {
  goalById(id: "42") {
    id
    title
    confidence
    progress
    targets { id title type progress }
    resources { id type title }
  }
}
```

The REST equivalent is several round trips (`/goals/42`, `/goals/42/targets`,
`/goals/42/resources`, …) or one oversized endpoint. Schema:
`backend/src/main/resources/graphql/schema.graphqls`; try it in GraphiQL at
`http://localhost:8080/graphiql.html` ([`docs/graphiql-guide.md`](docs/graphiql-guide.md)).

---

## Tech stack

### Backend

- **Java 17**, **Spring Boot 3.5**, **Spring for GraphQL**
- **PostgreSQL 16** with **pgvector** (`pgvector/pgvector:pg16` locally, **Neon** in production),
  **Flyway** migrations, **Spring Data JPA / Hibernate**
- **Spring Security** — Google OAuth2 / OIDC, CSRF on every mutation, sessions via **Spring Session
  JDBC**, a database-backed rate limiter shared by every instance
- **AI** — bring-your-own-key, several providers behind one interface, SSE streaming, native tool
  calling; keys encrypted at rest (AES-256-GCM)
- **Web Push** — RFC 8291 encryption and RFC 8292 VAPID on the JDK's own crypto, no third-party push
  library
- Build: **Maven Wrapper** (`backend/mvnw`, `mvnw.cmd`) — Maven itself does not need installing

### Web frontend

- **React 19**, **TypeScript**, **Vite 7**
- **TanStack Router** (file-based routes), **Zustand** (state + optimistic sync)
- **Tailwind CSS 4**, **Radix UI** primitives, Spira's own components in `src/components/spira/`
- **Gravity UI** icons, **TipTap** (the note editor), **react-day-picker**, **sonner**, **vaul**
- A service worker (`public/sw.js`) for Web Push notifications

### Android

- **Kotlin**, **Jetpack Compose**, **Apollo Kotlin** (typed GraphQL from the shared schema)
- Credential Manager for Google sign-in, Firebase Cloud Messaging, Firebase Crashlytics
- The note editor is the web's TipTap editor bundled into a WebView
  ([`docs/mobile-note-editor.md`](docs/mobile-note-editor.md))

### Testing

**Vitest**, **Playwright**, **pytest**, **JUnit 5 / Spring Boot Test / Spring GraphQL Test**,
**Robolectric** and **Compose UI Test**, plus **JaCoCo** and **Allure** reports. See [Tests](#tests).

---

## Run it locally

### Prerequisites

- **Node.js 20+** and npm
- **Java 17+** (the Maven Wrapper downloads Maven)
- **Docker** — running, not merely installed (`docker info` should answer)

### 1. Database first

The backend will not start without it, and that is the most common way a local run fails before
it starts.

```bash
docker compose -f backend/docker-compose.yml up -d postgres
```

It listens on `5432` with database, user and password all `spira` (`backend/docker-compose.yml`).

### 2. Backend on the `local` profile — no Google sign-in

```powershell
# Windows PowerShell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

```bash
# macOS / Linux
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile ([`LocalDevAuthFilter`](backend/src/main/java/com/spiramindscape/backend/auth/LocalDevAuthFilter.java))
signs every request in as `dev@local` and turns CSRF off, so the app opens straight onto All goals.
It also runs the reminder dispatcher in-process every minute and gives Web Push a key pair of its
own, so reminders and notifications work locally with no setup.

> Pass the profile as a Maven argument as shown. `SPRING_PROFILES_ACTIVE` works only if it is set
> in the **same** terminal that runs `mvnw`; set in another one, the backend silently starts
> without it and you get the login page.

Check it is active — the log says `The following 1 profile is active: "local"`, and:

```bash
curl -i http://localhost:8080/api/auth/me   # → 200 with dev@local, not 401
```

The dev user is found-or-created, so data and AI keys saved under it survive restarts.

To run with **real Google login** instead, start without the profile and provide
`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
([`docs/google-oauth-implementation-guide.md`](docs/google-oauth-implementation-guide.md)).

### 3. Frontend

```bash
npm install
npm run dev
```

Open `http://localhost:5173` — in a fresh or private tab if you have signed in with a real account
before, so an old session cookie does not mask the auto-login.

### 4. Stop everything

`Ctrl+C` in the backend and frontend terminals, then
`docker compose -f backend/docker-compose.yml down`. If a terminal was closed with a server still
running, find it by port and stop it:

```powershell
netstat -ano | Select-String ":8080"     # or :5173
Stop-Process -Id <PID from the LISTENING line> -Force
```

### Testing on a phone over a tunnel

The dev server is reachable on the LAN (`host: true`), which only helps a phone on the same Wi-Fi.
For any network, mobile data included, one script opens a public tunnel and starts the server in
the right order:

```powershell
.\tunnel-start.ps1 -Build            # cloudflared + the built bundle   <-- normally this
.\tunnel-start.ps1                   # cloudflared + the dev server (hot reload)
.\tunnel-start.ps1 -Provider ngrok   # ngrok, on the static domain in .env.local
```

- **The order matters.** Vite reads its host allow-list once at startup, and a cloudflared quick
  tunnel gets a new hostname every run, so the script starts the tunnel, writes the hostname to
  `.env.local` (`NGROK_URL`, gitignored), and only then starts the server.
- **Prefer `-Build`.** One cold load of the dev server is 6.17 MB over 103 requests against
  1.37 MB over 19 for the built bundle — the difference that exhausted a 1 GB ngrok allowance in
  days and makes free relays fail mid-load. You lose hot reload.
- **The proxy rewrites `Origin`.** The backend's CORS list cannot know a hostname minted this
  morning, and a browser sends its real `Origin` on every POST — so without the rewrite a phone got
  **403 on every GraphQL call** while the page loaded fine and `curl` (which sends no `Origin`)
  passed. It is dev-only: production has no proxy. **If you touch the proxy, check it from a
  browser, not curl.**
- **Real Google login over a tunnel** needs `FRONTEND_URL` set to the public URL and that URL's
  `/login/oauth2/code/google` added to the OAuth client's redirect URIs — a new entry for every
  quick-tunnel hostname, which is the argument for ngrok's static domain.

> ⚠️ **Anyone with a tunnel link is inside the app.** Under the `local` profile every request is
> `dev@local` — full access to the local database, real API keys included. A random hostname is
> obscurity, not authentication. Do not post one anywhere public.

---

## Android app (native)

> **Reviewing the project? Start with [`MOBILE.md`](MOBILE.md)** — the app's stage, its tests, and
> how to try it.

The app lives in [`android/`](android/) and talks to the same backend: the GraphQL API, plus
Google sign-in via `POST /api/auth/google/mobile`, which returns the same server-side session the
web uses. More in [`android/README.md`](android/README.md) and
[`docs/mobile-setup-guide.md`](docs/mobile-setup-guide.md).

**Open the `android/` folder in Android Studio, not the repository root** — the folder that holds
`settings.gradle.kts` is the project. Gradle needs **JDK 17–21**; Android Studio's embedded JDK
(jbr 21) works, a system JDK 22 does not.

### Build types — skip Google login for quick local checks

The login is not the app's decision: on start it asks `/api/auth/me` and believes the answer, so
which backend a build talks to decides whether there is a sign-in screen.

| Build type | Backend (`BuildConfig.API_BASE_URL`) | Sign-in | Cleartext HTTP |
|---|---|---|---|
| **`dev`** | `http://10.0.2.2:8080` — your machine, seen from the emulator | **none**, against a `local`-profile backend | yes |
| **`debug`** | production (Cloud Run) | real Google | yes |
| **`release`** | production (Cloud Run) | real Google | no |

`debug` points at production on purpose: it is what testers are sent, so a build made without
thinking is the safe one. For local work use `dev`:

```powershell
# the database and the backend on the `local` profile first (see above), then an emulator:
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <your-avd> `
    -no-window -no-snapshot-load -gpu swiftshader_indirect -no-boot-anim -no-audio
cd android; .\gradlew.bat :app:installDev
```

It is really the dev build when `/api/auth/me` answers `dev@local`, `versionName` ends in `-dev`
(`adb shell dumpsys package com.spiramindscape.android | Select-String versionName`), and the
screen shows All goals with no sign-in.

- A real phone on the LAN or a tunnel: `.\gradlew.bat :app:installDev -PspiraApiBaseUrl=<url>`
  rather than editing a committed file.
- `dev` and `debug` share an `applicationId`, so only one is installed at a time.

### Build and distribute

```powershell
cd android
.\gradlew.bat :app:assembleDebug        # the production-pointing APK
.\gradlew.bat :app:testDebugUnitTest    # unit, Compose and visual-check tests
.\gradlew.bat distributeDebug -PreleaseNotes="what changed"   # Firebase App Distribution
```

`distributeDebug` emails testers a download link, and **refuses to run with
`-PspiraApiBaseUrl` set**, so an emulator build can never be sent to a phone. Why Gradle here and
Maven for the backend: [`docs/build-tools.md`](docs/build-tools.md).

---

## AI coach

Open a goal and choose **ai coach** in the header. The coach reads the goal's data and attached
resources, can search the web, and proposes changes as cards — accept, edit or dismiss. Nothing
changes until you accept.

The design is **bring-your-own-key**: you paste your own provider key in the coach's key sheet.
Keys are encrypted at rest and never returned to the browser after saving (only a masked hint);
you can store several and switch the active one.

| Provider | Notes |
|---|---|
| **Anthropic (Claude)** | Recommended — the most reliable tool calling and multilingual conversation. |
| **Google Gemini** | A free way to try it: a key from <https://aistudio.google.com/app/apikey>. Use a model with tool calling, such as `gemini-2.5-flash`. |
| **Mistral** | Has a free tier within its [limits](https://admin.mistral.ai/plateforme/limits). |
| **OpenAI** | An `sk-…` key from platform.openai.com. |
| **Cohere** | Chat models from Cohere's own list. |
| **Tavily** | Web search only, not a chat model — add a free key to let the coach look things up. |

Small or instruct-only models tend to ignore tool calls, so their proposals never appear.

**GROW sessions** are the coach run as a timed session through Goal → Reality → Options → Will.
Its method is written out in `backend/src/main/resources/prompts/grow/coach-method.md`. A session
closes in three steps — a record of the session for the next one, the proposed changes, then the
goodbye — and **End** always leaves at once, with no AI involved. The book-retrieval (RAG) library
the sessions once used is retired and empty; why is in
[`docs/grow-sessions-rag-guide.md`](docs/grow-sessions-rag-guide.md).

Implementation: [`docs/ai-integration.md`](docs/ai-integration.md),
[`docs/ai-configuration.md`](docs/ai-configuration.md), [`docs/ai-testing.md`](docs/ai-testing.md),
[`docs/vision-image-support-guide.md`](docs/vision-image-support-guide.md), and the rules in
[`docs/ai-acceptable-use.md`](docs/ai-acceptable-use.md).

---

## Authentication and security

- **Google Sign-In only** (OAuth2 / OIDC authorization code flow). Identity is keyed on Google's
  `sub`, so it survives an email change; an `app_user` row is created on first sign-in.
- **Owner-scoped data.** Every lookup goes through the signed-in user
  (`findByIdAndUserId`); another user's id answers `NOT_FOUND`, never their data.
- **Server-side sessions in PostgreSQL** (`spring_session`), so they survive scale-to-zero.
  Cookies are `HttpOnly`, `SameSite=Lax`, and `Secure` in production; sessions last 14 days of
  inactivity.
- **CSRF** on every mutation, **rate limits** shared across instances, server-side validation of
  every input, and an allowlist for the push endpoints the server will call.
- **Encrypted at rest** with one key (`AI_ENCRYPTION_KEY`, AES-256-GCM): AI provider keys, the
  Google refresh token used for Docs export, and the server's Web Push private key.
- **Logging never carries the user's own text or any secret**, and a test fails the build if it
  does ([`docs/logging.md`](docs/logging.md)).
- Test-only logins exist for the **`local`** profile (above) and the CI-only **`e2e`** profile
  (an `X-E2E-Auth` header for the API tests). Production activates neither.

The full model, written for beginners: [`docs/security-model.md`](docs/security-model.md).

---

## Deployment

Spira runs on **Google Cloud Run** as one container serving both the API and the built SPA, so
cookies and CSRF work on a single origin, backed by **Neon** serverless PostgreSQL — both on free
tiers. The multi-stage `Dockerfile` builds the SPA, embeds it in the jar and runs it on a slim JRE;
Flyway migrates on startup. Full runbook, with every gotcha met along the way:
[`docs/deploy-gcp-cloud-run.md`](docs/deploy-gcp-cloud-run.md).

### Continuous deployment

A push to `main` deploys **only after** the frontend, backend, Python E2E and secret-scan jobs are
green. GitHub reaches GCP **keylessly** through Workload Identity Federation — no service-account
key exists in the repository. The deploy never touches the service's env vars or secret bindings,
so they persist across revisions. One-time setup: `deploy/setup-github-cd.ps1`, then the three
values it prints as repository **Variables** (`GCP_PROJECT_ID`, `GCP_WIF_PROVIDER`,
`GCP_DEPLOY_SA`). A one-off manual deploy: `.\deploy.ps1`.

### Secrets

The database password, the Google client secret and `AI_ENCRYPTION_KEY` live in **Secret
Manager**, never in the repository.

> ⚠️ **Do not rotate `AI_ENCRYPTION_KEY` without clearing `web_push_vapid_key` as well.** The
> stored Web Push private key can no longer be read, so push turns off (the app says so). Deleting
> the row makes a fresh pair on the next push:
>
> ```sql
> DELETE FROM web_push_vapid_key;
> ```
>
> Browsers re-subscribe to the new key the next time they open Spira; until then they get no
> notifications. The same rotation also makes every saved AI key and Google refresh token
> unreadable, so users have to add their AI keys again and reconnect Google Docs export.

### Reminders in production

Reminders need **Cloud Scheduler** to call the dispatcher once a minute with an OIDC token, and the
two env vars that tell the app which token to accept. Web Push keys need no setup: the server makes
its own pair and keeps it in the database. Commands:
[`docs/reminders-and-web-push.md`](docs/reminders-and-web-push.md) §3.

### Backups

`.github/workflows/db-backup.yml` takes an encrypted logical dump of production every night and
keeps it as a workflow artifact. Restoring:
[`docs/database-backup-and-restore.md`](docs/database-backup-and-restore.md).

---

## Tests

| Level | Where | Run |
|---|---|---|
| Web unit and component (Vitest) | `src/**/*.test.ts(x)` | `npm test` |
| Lint and types | — | `npm run lint`, `npx tsc --noEmit` |
| Backend unit + GraphQL / REST integration (JUnit, H2) | `backend/src/test/` | `cd backend && ./mvnw test` |
| API end-to-end against real PostgreSQL (pytest) | `tests-e2e/` | see below |
| Web end-to-end in a real browser (Playwright) | `e2e/` | `npm run test:e2e` |
| Android unit, Compose UI and visual checks (Robolectric) | `android/app/src/test/` | `cd android && ./gradlew :app:testDebugUnitTest` |

**Python E2E** drives a running backend over HTTP, with Flyway migrating a real PostgreSQL. The
backend has to run on the `e2e` profile, which enables the test-only `X-E2E-Auth` login:

```bash
SPRING_PROFILES_ACTIVE=e2e ./mvnw -f backend/pom.xml spring-boot:run
pip install -r tests-e2e/requirements.txt
SPIRA_BASE_URL=http://localhost:8080 pytest tests-e2e/ -v
```

**Playwright** needs the whole stack up — the database, the backend on the `local` profile, and
Vite (which it starts itself if nothing is on `:5173`). Run one spec while iterating:
`npx playwright test e2e/<file>.spec.ts`.

Conventions worth knowing before adding a test — boundary tests bind to the production constant
rather than a magic number, error paths assert both the classification and the message,
`@ParameterizedTest` for input matrices — are in [`docs/testing-guide.md`](docs/testing-guide.md),
with [`docs/unit-vs-integration-tests.md`](docs/unit-vs-integration-tests.md) for choosing a level
and [`docs/accessibility-testing.md`](docs/accessibility-testing.md) for the a11y checks.

### CI (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request, on
demand, and every night at 03:00 UTC.

| Job | What it does |
|---|---|
| Frontend tests and build | `npm ci`, Vitest, `npm audit` (critical blocks), the build, and a check that the committed note-editor bundle matches its source |
| Backend tests | `mvnw test` with a JaCoCo coverage summary |
| Android tests | unit, Compose and visual-check tests with coverage |
| Secret scan | gitleaks over the history (`.gitleaks.toml`) |
| Dependency vulnerability scan | Trivy over every lockfile; a fixable CRITICAL fails |
| Python E2E tests | the backend jar on the `e2e` profile against a PostgreSQL service container |
| Web E2E (Playwright) | the full stack in CI, then every spec in Chromium |
| Allure report | backend and E2E results merged into one HTML report |
| Deploy to Cloud Run | only on a push to `main`, only after the jobs above are green |

The nightly run is what catches a **new CVE in an unchanged dependency**; **Dependabot**
(`.github/dependabot.yml`) opens the update PRs for npm, Maven, Gradle, pip and the Actions
themselves. Reports (`allure-report`, `backend-jacoco-report`, Surefire XML) are downloadable
from each run's **Artifacts**. More in [`docs/github-actions-ci.md`](docs/github-actions-ci.md) and
[`docs/reading-coverage-reports.md`](docs/reading-coverage-reports.md).

---

## Where things live

| Path | What |
|---|---|
| `src/routes/` | pages — `index` (All goals), `goals.$goalId` (the workspace), `calendar`, `settings`, `login`, `remind.$eventId` (decide now) |
| `src/components/spira/`, `src/components/ui/` | product components; themed primitives |
| `src/lib/spira/` | domain logic — `types`, `progress`, `store` (Zustand + optimistic sync), `api` (GraphQL client), `auth` (session + CSRF), `upcoming`, `reminders`, `web-push` |
| `embeds/note-editor/` | the TipTap editor bundled for Android's WebView (`npm run build:note-editor`) |
| `backend/.../` | `graphql/`, domain packages `goal/` `target/` `resource/` `upcoming/`, `auth/`, `ai/`, `push/`, `security/`, `config/`, `logging/` |
| `backend/src/main/resources/` | `graphql/schema.graphqls`, `db/migration/` (Flyway), `prompts/` (the AI's prompts) |
| `android/` | the native app |
| `e2e/`, `tests-e2e/` | Playwright specs; Python API tests |
| `docs/`, `specs/` | guides; mission, roadmap, tech stack and dated feature specs |
| `backlog/` | the bug tracker — one Markdown file per bug |

### Database migrations

Flyway applies `backend/src/main/resources/db/migration/` in order on startup
([`docs/flyway-guide.md`](docs/flyway-guide.md)). The numbering jumps from V22 to V40 on purpose:
V23–V39 are left for a line of work still on another branch, and Flyway needs the versions in
order, not consecutive.

| Migrations | What they add |
|---|---|
| V1–V6 | goals, reality, options, targets, resources; timestamps as `TIMESTAMPTZ`; option order; confidence history |
| V7–V8 | per-user AI provider keys (encrypted); longer resource labels |
| V9–V12 | Google-authenticated users, goal ownership, the encrypted refresh token, notes linked to Google Docs |
| V13 | the `book_chunk` table and pgvector column (the retired GROW library) |
| V14 | Spring Session JDBC |
| V15 | device tokens for Firebase Cloud Messaging |
| V16–V17 | option status, decoupled from the active option |
| V18–V19, V22 | the AI chat transcript, the preferred provider, the live GROW session |
| V20 | the target progress lock |
| V21 | rate-limit buckets shared by every instance |
| V40–V42 | Upcoming events and their reminders, with an optimistic-lock version |
| V43 | the server's own Web Push key pair |

---

## Documentation

### Guides (`docs/`)

| Area | Documents |
|---|---|
| Running and deploying | [deploy-gcp-cloud-run](docs/deploy-gcp-cloud-run.md) · [database-backup-and-restore](docs/database-backup-and-restore.md) · [github-actions-ci](docs/github-actions-ci.md) · [build-tools](docs/build-tools.md) · [flyway-guide](docs/flyway-guide.md) · [graphiql-guide](docs/graphiql-guide.md) |
| Security and operations | [security-model](docs/security-model.md) · [logging](docs/logging.md) · [crash-reporting-and-monitoring](docs/crash-reporting-and-monitoring.md) · [google-oauth-implementation-guide](docs/google-oauth-implementation-guide.md) · [google-drive-integration-guide](docs/google-drive-integration-guide.md) |
| Notifications | [reminders-and-web-push](docs/reminders-and-web-push.md) · [push-notifications-guide](docs/push-notifications-guide.md) (Android, FCM) |
| AI | [ai-integration](docs/ai-integration.md) · [ai-configuration](docs/ai-configuration.md) · [ai-testing](docs/ai-testing.md) · [ai-acceptable-use](docs/ai-acceptable-use.md) · [vision-image-support-guide](docs/vision-image-support-guide.md) · [grow-sessions-rag-guide](docs/grow-sessions-rag-guide.md) · [ai-mini-apps-plan](docs/ai-mini-apps-plan.md) |
| Testing | [testing-guide](docs/testing-guide.md) · [frontend-testing-guide](docs/frontend-testing-guide.md) · [unit-vs-integration-tests](docs/unit-vs-integration-tests.md) · [accessibility-testing](docs/accessibility-testing.md) · [reading-coverage-reports](docs/reading-coverage-reports.md) · [test-coverage-report](docs/test-coverage-report.md) · [test-audit-report](docs/test-audit-report.md) · [linting-guide](docs/linting-guide.md) |
| Android | [mobile-setup-guide](docs/mobile-setup-guide.md) · [apollo-graphql-guide](docs/apollo-graphql-guide.md) · [mobile-note-editor](docs/mobile-note-editor.md) · [maestro-e2e-guide](docs/maestro-e2e-guide.md) · [google-fonts-compose](docs/google-fonts-compose.md) |
| Design | [changing-fonts](docs/changing-fonts.md) · [drag-and-drop-options](docs/drag-and-drop-options.md) · [specs/icon-sets.md](specs/icon-sets.md) |

### Specs (`specs/`)

[`mission.md`](specs/mission.md), [`tech-stack.md`](specs/tech-stack.md) and
[`roadmap.md`](specs/roadmap.md), then one dated folder per feature (requirements, plan,
validation) — from the 2026-05 frontend MVP and backend foundation through Google sign-in, the AI
proposal cards, security hardening, the native app and its design parity, inline attachments, the
target lock, the Android AI assistant, and the 2026-10 Upcoming reminders.

For anyone — person or AI agent — about to change code, [`CLAUDE.md`](CLAUDE.md) holds the working
rules: the definition of done, logging, security, and every design rule.
