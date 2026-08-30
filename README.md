# Spira

A full-stack goal-tracking web application built as a personal learning project. Spira helps users set, structure, and track long-term personal goals using the GROW coaching model — Goal, Reality, Options, Will.

**🌐 Live:** https://spira-952567559986.europe-west1.run.app

> ⏳ **First load takes a few seconds — this is expected, not a bug.** The app runs on
> Cloud Run's free tier, which **scales to zero**: when no one has used it for a while,
> the container is shut down to stay free. Your first visit triggers a **cold start** —
> Cloud Run spins a container back up and Spring Boot boots (JVM start, DB connection,
> Flyway check). That takes roughly **5–15 seconds the first time**; every request after
> that is instant until it goes idle again. So please give it a moment to wake up rather
> than assuming it's broken.

### Two ways to use Spira

- **Just use it / show someone** → open the **live URL** above. It's the production
  deployment with real **Google sign-in** and per-user data — each person signs in with
  their own Google account and sees only their own goals.
- **Develop or test locally without signing in** → run it on your machine under the
  **`local` Spring profile**, which auto-logs-in a throwaway dev user (no Google, no login
  screen). Full instructions: [Skip Google login for quick local checks](#skip-google-login-for-quick-local-checks--the-local-profile).
  This profile is **dev-only** — production never uses it.

## Deployment

Spira runs on **Google Cloud Run** as a single container that serves both the Spring Boot API and the built React SPA on **one origin** (so the Google-OAuth session cookies + CSRF work), backed by a **Neon** serverless PostgreSQL — both on free tiers. The image is a multi-stage `Dockerfile` (build SPA → embed it in the jar → slim JRE); Flyway applies the DB migrations on startup. Secrets (DB password, Google client secret, AI encryption key) live in **Secret Manager**.

### Continuous deployment (CI/CD)

Pushes to `main` **auto-deploy** once the test suite is green. The `deploy` job in
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs **only on push to `main`** and
**only after the `frontend`, `backend`, and `e2e` jobs pass** (`needs: [...]`) — so a
failing build never ships. It then builds the same root `Dockerfile` and rolls out a new
Cloud Run revision with `gcloud run deploy spira --source .`.

GitHub authenticates to GCP **keylessly** via **Workload Identity Federation** — GitHub's
short-lived OIDC token is exchanged for temporary GCP credentials, so there is **no
service-account key stored in the repo**. The provider is locked to this repository only.

**One-time setup** (run once by a project Owner):

1. Provision WIF + the deployer service account:
   ```powershell
   gcloud config set project YOUR_PROJECT_ID
   .\deploy\setup-github-cd.ps1
   ```
   The script ([`deploy/setup-github-cd.ps1`](deploy/setup-github-cd.ps1)) creates a
   Workload Identity Pool, a GitHub OIDC provider restricted to `alakhmakova/spira-mindscape`,
   a `github-deployer` service account with the deploy roles, and the impersonation binding.
2. Add the three values it prints as **GitHub repo Variables** (Settings → Secrets and
   variables → Actions → **Variables**): `GCP_PROJECT_ID`, `GCP_WIF_PROVIDER`,
   `GCP_DEPLOY_SA`. (Not secrets — none are sensitive.)
3. Push to `main`. Tests run; on success the **Deploy to Cloud Run** job ships the revision.

Env vars and Secret Manager bindings live on the **service** and persist across revisions,
so the deploy job never re-sets them (it can't clobber `FRONTEND_URL`/CORS/secrets).

Full runbook (initial setup + every gotcha hit along the way, incl. the CD section §11) is
in [`docs/deploy-gcp-cloud-run.md`](docs/deploy-gcp-cloud-run.md).

### Manual deploy

For a one-off deploy outside CI, run `.\deploy.ps1` from the repo root (it applies env +
secrets and can do a full `--source` rebuild). Useful for hotfixes or before CD is set up.

Detailed project documentation is in the repository root folders:

- `docs/` — practical guides and testing/linting docs
- `specs/` — mission, roadmap, architecture and implementation specs

## Why This Project Exists

I graduated from Jensen Yrkeshögskola in May 2026, specialising in systems development with a focus on testing. This project is how I learn best — by building something real, making mistakes, and figuring out why things work the way they do.

The idea came during my web application development course, when I wanted to understand exactly how a backend and frontend connect and communicate. I rebuilt this project several times as my understanding improved.

At some point, I learned that clear specs and tests are the best form of documentation:

- they verify behavior
- they catch regressions
- they make intent explicit for collaborators and AI tools

## The Problem I Was Solving

In summer 2023 I moved to Sweden with one large goal: to build a life here. In practice that meant many parallel goals — language, education, work, social integration. I needed one place to structure all of it and track visible progress.

I first tried plain notes, then traditional task-management apps, and then goal-tracking products. Most tools were still focused on work-style processes (OKRs, team performance, reporting), while I needed a personal long-term goal system.

So I built Spira.

## What the Application Does

Spira is a structured goal-setting workspace. For each goal, a user can manage:

- **Goal** — title, description, confidence (1..10), deadline
- **Reality** — actions already taken and current obstacles
- **Options** — possible strategies and selected option
- **Will / Targets** — measurable execution using:
  - binary target (done / not done)
  - numeric target (e.g. current/total)
  - checklist target (sub-items)
- **Resources** — notes, links, files, email/contact resources
- **AI coach** — a goal-scoped chat assistant that can read the goal's data and resources, run web search, and propose changes (new targets, edits, option selection, notes) that the user approves before they are applied. See [AI Coaching Assistant](#ai-coaching-assistant).
- **GROW sessions** — a timed, structured coaching session following the GROW model (Goal → Reality → Options → Will), grounded in excerpts from real coaching books retrieved via pgvector RAG. Session history is persisted and carried into future sessions. See [GROW Sessions](#grow-sessions).

Goal progress is calculated from targets and exposed both in frontend and backend.

Authentication is **Google Sign-In only** — users sign in with their Google account and get a fully private workspace; data is never shared across accounts. See [Authentication & Security](#authentication--security).

---

## Tech Stack

### Backend

- Language: **Java 17** (`backend/pom.xml`)
- Framework: **Spring Boot 3.5.15**
- API layer: **GraphQL** (`spring-boot-starter-graphql`)
- Database: **PostgreSQL 16** (Docker) + **Flyway** migrations
- ORM: **Spring Data JPA / Hibernate**
- Build tool: **Maven Wrapper** (`backend/mvnw`)
- Tests: **Spring Boot Test + Spring GraphQL Test + H2 (test profile)**, plus **Python E2E** (`pytest`) against a real PostgreSQL
- AI: **multi-provider, bring-your-own-key** (Anthropic / OpenAI / Mistral / Google Gemini + Tavily web search), SSE streaming, native tool calling. Keys are encrypted at rest (AES-256).

### Frontend

- **React 19 + TypeScript + Vite 7**
- **TanStack Router** (file-based routes)
- **Zustand** (app state)
- **Tailwind CSS 4** + **Radix UI** components
- **TipTap** (rich text editor)
- **Vitest** (tests), **ESLint**, **Prettier**

---

## Full Local Run (Frontend + Backend + DB)

### 1) Prerequisites

- Node.js 20+
- npm
- Java 17+
- Docker (for PostgreSQL)

### 2) Start PostgreSQL

```bash
cd backend
docker compose up -d postgres
docker compose ps
```

Expected DB settings (`backend/docker-compose.yml`):

- DB: `spira`
- user: `spira`
- password: `spira`
- port: `5432`

### 3) Start backend

```bash
cd backend
sh ./mvnw spring-boot:run
```

Backend URLs:

- GraphQL endpoint: `http://localhost:8080/graphql`
- GraphiQL UI: `http://localhost:8080/graphiql.html`

#### Skip Google login for quick local checks — the `local` profile

The app is auth-gated and user-scoped, so normally you'd sign in with Google every
time (and configure a `localhost` redirect URI in the Google Console). For fast UI
checks that's friction. Start the backend under the **`local` Spring profile** instead:
it auto-logs-in a fixed dev user (`dev@local`) on every request, so you open
`http://localhost:5173` and land straight inside — **no login screen**.

Pass the profile **as a Maven argument** (recommended — it can't be lost between shells):

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

> Prefer the `-D…` argument over the `SPRING_PROFILES_ACTIVE` env var: the env var only
> works if it's exported **in the same terminal** that then runs `mvnw`. Setting it in one
> command and running the backend in another (separate shell) silently starts **without**
> the profile — you'd still see the login page.

**Verify it's active.** On startup the backend logs:

```
The following 1 profile is active: "local"
```

and an anonymous request returns the dev user instead of `401`:

```bash
curl -i http://localhost:8080/api/auth/me   # → 200 with dev@local
```

Then open `http://localhost:5173` (use a fresh/incognito tab if you previously signed in
with a real account, so an old session cookie doesn't mask the auto-login).

Notes:

- The dev user is found-or-created, so AI keys and data you save persist across restarts
  (add your AI key once in the AI panel; it stays for that user).
- `local` also disables CSRF (the auto-login is stateless), mirroring the CI-only `e2e`
  profile. See [`LocalDevAuthFilter`](backend/src/main/java/com/spiramindscape/backend/auth/LocalDevAuthFilter.java).
- **Dev-only and safe:** production never activates `local`, so the bean doesn't exist
  there and full Google OAuth + CSRF are enforced. Never run a deployed instance with
  this profile.

### 4) Start frontend

```bash
cd .
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

### 5) Stop everything

- Stop frontend/backend terminals with `Ctrl+C`
- Stop DB:

```bash
cd backend
docker compose down
```
#### If the terminals were closed but the servers are still running, find the processes by port and stop them:

```powershell
# Backend on port 8080
netstat -ano | Select-String ":8080"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force

# Frontend on port 5173
netstat -ano | Select-String ":5173"
Stop-Process -Id <PID_FROM_LISTENING_LINE> -Force
Stop-Process -Id 26928 -Force

```

### Windows equivalents

Backend run:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend run:

```powershell
npm install
npm run dev
```

---

## Testing on Mobile via ngrok

Vite's dev server is LAN-accessible (`host: true` in `vite.config.ts`), but that only works when the phone is on the **same Wi-Fi**. For a device on a different network (e.g. mobile data) you need a public tunnel. The project is pre-configured for **ngrok**.

### How it works

The Vite dev server proxies every backend route (`/api`, `/graphql`, `/oauth2`, `/login`) to Spring Boot at `localhost:8080`. So a single ngrok tunnel on port **5173** is enough — the mobile browser hits one public URL and Vite handles the rest internally.

OAuth is the tricky part: Spring builds the `redirect_uri` (`/login/oauth2/code/google`) from the incoming `Host` header. With `changeOrigin: true` the proxy would set `Host: localhost:8080` and Spring would build `http://localhost:8080/login/oauth2/code/google` — which the phone can't reach. The fix: `vite.config.ts` reads `NGROK_URL` from `.env.local` and injects `X-Forwarded-Host` + `X-Forwarded-Proto` headers on OAuth proxy routes so Spring builds the correct public redirect URI. Spring trusts these headers because `server.forward-headers-strategy=framework` is set. Vite also adds the ngrok domain to `server.allowedHosts` so the host-check guard doesn't block the request.

### One-time setup

1. **Get a free static ngrok domain** at <https://dashboard.ngrok.com/domains> (one domain per free account). Static domain means the URL never changes and you only add the Google redirect URI once.

2. **Add the redirect URI in Google Cloud Console** ([console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials)) → your OAuth 2.0 Client → **Authorized redirect URIs**:
   ```
   https://<your-static-domain>.ngrok-free.app/login/oauth2/code/google
   ```

3. **Save the URL in `.env.local`** (gitignored, created in the project root):
   ```
   NGROK_URL=https://<your-static-domain>.ngrok-free.app
   ```
   Vite reads this file automatically on startup — no environment variable exports needed.

### Starting everything (daily workflow)

**One script does the whole sequence** — tunnel, allow-list, server, and a check that the URL
actually answers before it prints:

```powershell
.\tunnel-start.ps1 -Build            # cloudflared + the built bundle  <-- normally this
.\tunnel-start.ps1                   # cloudflared + the dev server (hot reload)
.\tunnel-start.ps1 -Provider ngrok   # ngrok, on the static domain in .env.local
```

**The order is not a preference.** Vite reads its host allow-list **once at startup** from
`NGROK_URL` in `.env.local`, and a cloudflared quick tunnel gets a fresh random hostname every run.
Start the server first and every request comes back `Blocked request. This host … is not allowed`.
The script exists to get this right: tunnel → write `.env.local` → start the server.

#### Prefer `-Build`

| Serving | One cold page load |
|---|---|
| Dev server | **6.17 MB** over **103 requests** |
| Built bundle (`-Build`) | **1.37 MB** over **19 requests** |

Vite's dev server sends uncompressed ES modules, one request per file — a single React chunk is
1,005,279 bytes. That ~6 MB per view is what exhausted a 1 GB ngrok allowance in a couple of days
(~165 page loads, `ERR_NGROK_725`), and it is why free relays such as localtunnel start returning
502 partway through a load. You lose hot reload with `-Build`; you gain a tunnel that survives.

#### Google login over a tunnel

Only needed if you are testing the real OAuth flow — the `local` profile signs you in
automatically and needs none of this.

`FRONTEND_URL` controls where `OAuth2LoginSuccessHandler` sends the browser after a successful
login, so it has to match the public URL:

```powershell
$env:FRONTEND_URL = "<the URL the script printed>"
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The same URL's `/login/oauth2/code/google` must also be added to **Google Cloud Console → Credentials
→ OAuth 2.0 Client → Authorized redirect URIs**. With a rotating quick-tunnel hostname that is a new
entry every run, which is the one real argument for ngrok's static domain — or for a named
Cloudflare tunnel on a domain you own.

> ⚠️ **Anyone with the link is inside the app.** Under the `local` profile `LocalDevAuthFilter`
> authenticates *every* request as `dev@local` — there is no login at all. A random hostname is
> obscurity, not authentication. Fine for an hour of testing; put Cloudflare Access in front of it
> if the link is going to live.
>
> The laptop also has to stay awake with Docker and the backend running: a tunnel is a pipe to your
> machine, not hosting.

### What was changed in the codebase

| File | Change |
|---|---|
| `vite.config.ts` | A `defineConfig(({ mode }) => {})` factory that reads `NGROK_URL` via `loadEnv()`. Adds that host to `server.allowedHosts` / `preview.allowedHosts`; injects `X-Forwarded-Host` / `X-Forwarded-Proto` on `/oauth2` and `/login` so Spring builds the right `redirect_uri`; presents an allow-listed `Origin` to the backend on the API proxy routes (see below); and gives `preview` the same proxy as `server` so `-Build` can reach the API. |
| `.env.local` | Written by `tunnel-start.ps1`; stores `NGROK_URL` — the current public URL, whichever provider made it. Gitignored (`*.local`). |
| `tunnel-start.ps1` | Starts cloudflared (or ngrok), waits for the hostname, writes `.env.local`, builds if asked, starts the server, and verifies the URL answers 200 before printing it. |

#### Why the proxy rewrites `Origin`

Spring's CORS allow-list (`app.cors.allowed-origins`) knows `localhost:5173` and the LAN. It cannot
know a hostname a tunnel minted this morning — and a **browser sends its real `Origin` on every
POST**. So a phone on a tunnel got **403 on every GraphQL call** while the page itself loaded fine:
the screen read "We couldn't sync with the backend" and nothing said why. `curl` did not reproduce
it, because curl sends no `Origin`.

The dev proxy therefore presents `Origin: http://localhost:5173` to the backend. This is safe
precisely because the proxy is dev-only: in production the container serves the SPA and the API on
one origin with no proxy in between, so none of that code runs.

**If you touch the proxy, verify with a browser, not curl.**

---

## Android app (native)

> **📱 Reviewing the project? Start with [`MOBILE.md`](MOBILE.md)** — the mobile app's current
> stage, what's done vs. planned, its tests, and how to download/try or run it locally.

A native **Kotlin / Jetpack Compose** app lives in [`android/`](android/). It reuses the same
backend as the web app — the GraphQL API plus Google sign-in via `POST /api/auth/google/mobile`
— so a user has one account across desktop web, responsive mobile web, and the native app. See
[`android/README.md`](android/README.md), [`docs/mobile-setup-guide.md`](docs/mobile-setup-guide.md),
and [`specs/2026-07-15-native-mobile-app/`](specs/2026-07-15-native-mobile-app/).

### Prerequisites

- **Android Studio** (installed). One-time tooling/Firebase/OAuth setup is in
  [`docs/mobile-setup-guide.md`](docs/mobile-setup-guide.md).
- **JDK 17–21** for Gradle. Android Studio uses its embedded JDK automatically; a system JDK 22
  is **not** supported by the Android Gradle Plugin.

### Open it in Android Studio

**Open the `android/` folder, _not_ the repository root.** Android Studio treats the folder
containing `settings.gradle.kts` as the project; the repo root holds the web + backend, so
opening it won't be recognized as an Android project.

1. Android Studio → **Open** → select `spira-mindscape/android` → OK.
2. Let **Gradle Sync** finish (first run downloads AGP/Kotlin/Compose).
3. If prompted about the JDK: **Settings → Build, Execution, Deployment → Build Tools → Gradle →
   Gradle JDK →** choose the **Embedded JDK (jbr, 21)**.

### Run on a device

- **Real phone (recommended):** enable **Developer options → Wireless debugging** (or USB
  debugging), pair it, pick it in the device dropdown, and Run ▶. A real device is the best
  target — real touch and performance.
- **Emulator:** create one in **Device Manager → Create Device** if you don't have a phone
  handy.
- The emulator reaches a locally-running backend at `http://10.0.2.2:8080` — which is what the
  **`dev` build type** points at (see below).

### Build variants — skip Google login for quick local checks

This is the Android twin of the backend's **`local` profile** above, and it works the same way,
because **the login is not the app's decision**. The app has no bypass of its own and must never
grow one: on start it asks `/api/auth/me` and believes the answer.

- Production answers `401` → the sign-in screen, real Google OAuth.
- A backend on the **`local` profile** signs every request in as `dev@local`
  ([`LocalDevAuthFilter`](backend/src/main/java/com/spiramindscape/backend/auth/LocalDevAuthFilter.java))
  → `me` returns a user and the app lands **straight on All goals, no login screen**.

So "no login on dev, a login on prod" is entirely a question of *which backend the build talks
to*, exactly as on the web, where `npm run dev` simply proxies to whichever backend is running.
On Android that choice is the **build type**:

| Build type | Backend baked into `BuildConfig.API_BASE_URL` | Sign-in | Cleartext HTTP |
|---|---|---|---|
| **`dev`** | `http://10.0.2.2:8080` — your machine, as seen from inside the emulator | **none**, when that backend runs the `local` profile | yes |
| **`debug`** | production (Cloud Run) | real Google sign-in | yes |
| **`release`** | production (Cloud Run) | real Google sign-in | no |

`debug` is what `distributeDebug` sends to testers, which is why it points at production:
**a build made without thinking about it is the safe one.**

#### Running the dev build

```powershell
# 1. the database, then the backend on the `local` profile (see "Full Local Run" above)
docker compose -f backend/docker-compose.yml up -d postgres
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 2. an emulator (Android Studio's Device Manager, or from the command line).
#    Headless is fine and is what an agent should use — windowed mode crashes on some GPUs:
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <your-avd> `
    -no-window -no-snapshot-load -gpu swiftshader_indirect -no-boot-anim -no-audio

# 3. the app
cd android; .\gradlew.bat :app:installDev
```

**Verify it's active** — three independent signals:

```powershell
curl -i http://localhost:8080/api/auth/me        # → 200 with dev@local, not 401
adb shell dumpsys package com.spiramindscape.android | Select-String versionName   # → 0.2.7-dev
adb exec-out screencap -p > shot.png             # → All goals, no sign-in screen
```

#### Testing against something other than the emulator

A real phone on the same Wi-Fi, or a cloudflared tunnel, is neither default. **Override the URL
rather than editing a committed file:**

```powershell
.\gradlew.bat :app:installDev -PspiraApiBaseUrl=http://<your-PC-LAN-IP>:8080
.\gradlew.bat :app:installDev -PspiraApiBaseUrl=https://<something>.trycloudflare.com
```

Notes:

- **`distributeDebug` refuses to run with `-PspiraApiBaseUrl` set.** The flag is baked into
  `BuildConfig` at assemble time, so a build made for the emulator and then distributed would
  send the owner an app pointing at `10.0.2.2`, which resolves to nothing on a phone. That used
  to be a line in the docs asking you to remember; it is now a build failure.
- **`dev` and `debug` share an `applicationId`**, so only one of them lives on a device at a
  time. `versionName` carries a `-dev` suffix, so which one is installed is a fact you can read
  rather than remember. (Giving `dev` its own `applicationIdSuffix` would let them sit side by
  side, but needs a second Android app registered in the Firebase console — `google-services.json`
  has one client today.)
- **A tunnel URL has no login on it.** Under the `local` profile every request is `dev@local`,
  so anyone holding the link is inside the app with full access to your local database, real API
  keys included. Don't post one anywhere public.
- **Dev-only and safe:** `release` never gets the local URL and never allows cleartext HTTP, and
  production never activates the `local` profile, so full Google OAuth is enforced there.

### Build from the terminal

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
.\gradlew.bat :app:assembleDebug     # APK → app/build/outputs/apk/debug/
.\gradlew.bat installDebug           # install on a connected device/emulator (production)
.\gradlew.bat installDev             # install pointed at the local backend (no login)
```

Why Gradle here and Maven for the backend? See [`docs/build-tools.md`](docs/build-tools.md).

---

## How Frontend and Backend Are Connected (with code references)

Connection flow in this project:

1. **Frontend sends GraphQL HTTP POST** to `/graphql` from `src/lib/spira/api.ts`.
2. **Vite dev proxy** forwards `/graphql` to backend `http://localhost:8080` (`vite.config.ts`).
3. **Spring GraphQL controller** handles queries/mutations in `backend/src/main/java/com/spiramindscape/backend/graphql/SpiraGraphqlController.java`.
4. **Services + JPA** save/fetch data (`goal`, `target`, `resource`, `reality`).
5. **PostgreSQL schema/migrations** are in `backend/src/main/resources/db/migration/`.

Concrete frontend trigger example:

- `AppShell` calls `loadGoals()` on mount (`src/components/shell/AppShell.tsx`)
- store method calls `spiraApi.fetchGoals()` (`src/lib/spira/store.ts`)
- API executes GraphQL `query Goals { goals { ... } }` (`src/lib/spira/api.ts`)

---

## Why GraphQL Instead of REST (project example)

### Real GraphQL shape used in Spira

From this project, a single query can request only needed nested fields:

```graphql
query {
  goalById(id: "42") {
    id
    title
    confidence
    progress
    targets {
      id
      title
      type
      progress
    }
    resources {
      id
      type
      title
    }
  }
}
```

Related files:

- schema: `backend/src/main/resources/graphql/schema.graphqls`
- resolver/controller: `backend/src/main/java/com/spiramindscape/backend/graphql/SpiraGraphqlController.java`
- frontend client query patterns: `src/lib/spira/api.ts`

### How this would look in REST

To get equivalent data, REST usually needs several endpoints/requests, for example:

- `GET /goals/42`
- `GET /goals/42/targets`
- `GET /goals/42/resources`
- optionally `GET /goals/42/reality`, `GET /goals/42/options`

Or one oversized endpoint returning more fields than this page needs.

That is the practical reason GraphQL fits Spira’s goal workspace screens.

### GraphQL Smoke Test

Open `http://localhost:8080/graphiql.html` and run:

```graphql
mutation {

  createGoal(input: { title: "Test Goal", description: "Test", confidence: 7 }) {

    id

    title

    createdAt

  }

}

```

Then open `http://localhost:5173` and confirm the goal appears in the frontend.

---

## Frontend Guide 

### What the frontend consists of

- `src/main.tsx` — app entry point
- `src/router.tsx` + `src/routeTree.gen.ts` — routing setup
- `src/routes/` — pages:
  - `index.tsx` — goals overview
  - `goals.$goalId.tsx` — goal workspace
  - `calendar.tsx` — calendar view
- `src/components/spira/` — product-specific components (GoalCard, Targets, Resources, etc.)
- `src/components/ui/` — reusable UI primitives
- `src/lib/spira/` — domain logic:
  - `types.ts` — core types
  - `progress.ts` — progress calculation
  - `store.ts` — Zustand state + optimistic sync
  - `api.ts` — GraphQL client

### How to find the component you need

1. Start from route file in `src/routes/`.
2. See which components it imports from `src/components/spira/`.
3. If component behavior updates backend data, check `src/lib/spira/store.ts` for action.
4. Then inspect matching GraphQL operation in `src/lib/spira/api.ts`.

---

## Backend Guide 

### What the backend consists of

- `backend/src/main/java/.../BackendApplication.java` — Spring Boot entry
- `.../graphql/SpiraGraphqlController.java` — GraphQL queries + mutations + batch resolvers
- `.../goal`, `.../target`, `.../resource` packages:
  - entities (`Goal`, `Target`, `Resource`, etc.)
  - services (`GoalService`, `TargetService`, `ResourceService`, `RealityService`)
  - repositories (`JpaRepository` interfaces)
- GraphQL schema: `backend/src/main/resources/graphql/schema.graphqls`
- Config: `backend/src/main/resources/application.properties`

### Request lifecycle (simple mental model)

GraphQL request -> controller method -> service business logic -> repository -> PostgreSQL -> response mapped back to frontend.

---

## Database Guide 

### Where DB is defined

- Docker PostgreSQL: `backend/docker-compose.yml`
- App connection config: `backend/src/main/resources/application.properties`
- Migrations: `backend/src/main/resources/db/migration/`

### Existing migrations

- `V1__init_database.sql` — core tables/indexes/triggers
- `V2__seed_data.sql` — intentionally empty seed
- `V3__timestamps_to_timestamptz.sql` — deadlines/achieved timestamps to `TIMESTAMPTZ`
- `V4__option_position.sql` — option ordering (`position`)
- `V5__resource_label_length.sql` — resource title/name constraints (20 chars)
- `V6__confidence_history.sql` — confidence history table
- `V7__ai_schema.sql` — per-user AI provider keys (AES-256-GCM encrypted at rest)
- `V8__resource_label_length_200.sql` — widens resource labels from 20 → 200 chars
- `V9__app_user.sql` — `app_user` table for Google-authenticated users (keyed on `google_sub`)
- `V10__goal_owner.sql` — adds `user_id` FK to goals for per-user data isolation
- `V11__app_user_refresh_token.sql` — stores encrypted Google refresh token for Drive API
- `V12__resource_google_doc.sql` — links a note resource to its exported Google Doc
- `V13__grow_books.sql` — `book_chunk` table + pgvector column for GROW RAG library
- `V14__spring_session.sql` — Spring Session JDBC schema (sessions survive scale-to-zero)

Flyway runs these in order on backend startup.

---

## AI Coaching Assistant

Spira ships with an AI chat scoped to a single goal. Create a goal manually, open the goal, click "ai coach" in the header. AI can read the goal's
data and attached resources, run web search, and **propose** changes (edit goal name, description, create actions and obstacles, options, set active option, set new target and change its progress, read and write resources). Nothing is applied until the user approves
the proposal — this is a core safety rule.

The design is **bring-your-own-key (BYOK)**: you paste your own provider API key in
the AI panel. Keys are encrypted at rest (AES-256) and never returned to the frontend
after saving (only a masked hint is shown). You can store keys for several providers
and switch the active one; your selection is remembered between sessions.

### Choosing a provider

| Provider | Status | Notes |
|---|---|---|
| **Anthropic (Claude)** | ✅ Recommended | Best user experience — the most reliable tool calling and multilingual conversation. |
| **Google Gemini** | ✅ Works (free option) | Good for testing without paying for an API. Free key from Google AI Studio. See the setup note below. |
| **Mistral** | ✅ Works | Free with Mistral Studio. Free mode. You can create API keys and use the free tier within the limits described on the limits page https://admin.mistral.ai/plateforme/limits. This free usage is included in your Vibe subscription, if you have one, or available by default. |
| **OpenAI** | ✅ Works | Paste an `sk-…` key from platform.openai.com. Supports `gpt-4o` and the `o3`/`o4-mini` reasoning models. |
| **Tavily** | 🔍 Web search only | Not a chat model. Add a free Tavily https://www.tavily.com/ key to give the coach web-search capability. |

### Using Google Gemini (free testing)

Gemini is a free way to try the coach:

1. **Get an API key** at <https://aistudio.google.com/app/apikey> (Google AI Studio → "Get API key"). The key starts with `AIza…`.

2. **Paste it into the AI panel**, pick a model, and start chatting.

**Pick a model that supports tool calling** — the ai coach relies on it. Recommended models:

- `gemini-2.5-flash` (fast, the default)
- `gemini-2.5-pro` (stronger reasoning)

Avoid tiny/instruct-only models: they tend to ignore tool calls, so proposals won't appear.

### Web search

Web search is optional and provided by **Tavily**. Add a Tavily https://www.tavily.com/ key in the AI panel to
let the coach look things up; without it the coach still works but cannot browse the web.

For implementation details (provider abstraction, streaming, proposal lifecycle, prompts)
see `docs/ai-integration.md`, `docs/ai-configuration.md`, and `docs/ai-testing.md`.

---

## GROW Sessions

GROW is a structured coaching model (Goal → Reality → Options → Will) built on top of the AI coach. Open a goal, start a GROW session, and the coach leads you through each phase within a fixed time limit.

**What makes it different from the regular AI chat:**

- **Book-grounded answers** — responses are anchored to excerpts from real coaching books retrieved via **pgvector RAG** (`mistral-embed` embeddings). The coach is not allowed to respond from the generic prompt alone; if the book library is empty it refuses with an error (no silent fallback to generic advice).
- **Session timing** — a visible timer paces the session. When the timer expires the frontend sends a hidden wrap-up prompt and shows the end card only after the coach's goodbye.
- **Session memory** — at the end of each session the coach writes a closing reflection that is saved to the goal (`goal.ai_memory`). The next GROW session picks this up and continues from where you left off.

**Setup requirements:**

1. **Mistral API key** — embeddings use `mistral-embed` (Anthropic has no embeddings API). Add your Mistral key in the AI panel. Without it the GROW session returns an error by design.
2. **Coaching books** — the owner places `.txt` files (UTF-8, blank-line paragraph breaks) in `backend/src/main/resources/books/`. Supported titles: `coaching-for-performance.txt`, `coach-the-person.txt`. On first backend start `BookIngestionRunner` chunks and embeds them (one-time; progress is streamed as SSE events). Re-ingest after changes: `DELETE FROM book_chunk WHERE book='<Title>'` + restart.

**Backend implementation:** `backend/src/main/java/com/spiramindscape/backend/ai/grow/` — `BookIngestionRunner`, `GrowLibraryService`, `MistralEmbeddingClient`, `GoalMemoryService`.

---

## Authentication & Security

### Google Sign-In

Spira uses **Spring Security OAuth2 / OIDC Authorization Code flow** — the only way to sign in is with a Google account. There are no passwords; identity is keyed on the Google `sub` claim (stable even if the user changes their email).

- Any Google account may sign in; a `app_user` row is created automatically on first login.
- Every goal is owned by a user (`goal.user_id`); cross-user access returns `NOT_FOUND` — data is fully private.
- The Google OAuth **refresh token** is stored encrypted (AES-256-GCM) so the backend can mint Drive access tokens without re-prompting the user.

**Session model:** server-side sessions stored in **PostgreSQL** (`spring_session` table, V14 migration) via Spring Session JDBC. This means sessions survive Cloud Run scale-to-zero and instance restarts. Cookies are `HttpOnly`, `SameSite=Lax`; `Secure` is enabled in production (behind Cloud Run's TLS termination via `server.forward-headers-strategy=framework`). Session lifetime is **14 days** of inactivity.

**Dev bypass (local profile only):** starting with `-Dspring-boot.run.profiles=local` skips Google and auto-logs in a fixed `dev@local` user. Production never uses this profile.

**E2E test bypass (e2e profile only):** CI cannot run real Google OAuth headlessly. The `e2e` profile enables an `X-E2E-Auth` HTTP header login used only by `pytest` tests. Never active in production.

### Security hardening

| Area | What was done |
|---|---|
| **Spring Boot CVE patches** | Upgraded 3.4.5 → **3.5.15**, patching 5 critical CVEs in transitive dependencies. |
| **Session serialization** | `AppUser` and `AppUserOidcUser` implement `Serializable` with explicit `serialVersionUID` — required for Spring Session JDBC to deserialize the principal from the DB correctly after restarts. |
| **No stored keys in repo** | Google client secret, DB password, and AI encryption key live in GCP Secret Manager; CI authenticates keylessly via Workload Identity Federation. |
| **HttpOnly + Secure cookies** | Session cookie is never accessible from JavaScript; `Secure` is enforced in production. |

---

## Tests: how to run and what exists

### Run tests locally

From repository root (frontend):

```bash
npm test
npm run build
```

From `backend/` (backend tests):

```bash
cd backend
sh ./mvnw test
```

Python E2E tests (run against a running backend + real PostgreSQL). The app is
auth-gated, so the backend must run under the `e2e` profile, which enables a test-only
`X-E2E-Auth` header login and disables CSRF (real Google OAuth can't run headlessly):

```bash
# start the backend under the e2e profile first
SPRING_PROFILES_ACTIVE=e2e ./mvnw -f backend spring-boot:run

# then, in another terminal
pip install -r tests-e2e/requirements.txt
SPIRA_BASE_URL=http://localhost:8080 pytest tests-e2e/ -v
```

> Background on why these tests were rewritten when auth landed:
> [docs/testing-guide.md → Why the E2E tests had to be rewritten](docs/testing-guide.md#why-the-e2e-tests-had-to-be-rewritten).

Windows equivalents:

```powershell
npm.cmd test
npm.cmd run build
cd backend
.\mvnw.cmd test
```

### Frontend test files (current)

- `src/lib/spira/api.contract.test.ts`
- `src/lib/spira/api.test.ts`
- `src/lib/spira/progress.test.ts`
- `src/lib/spira/store.test.ts`
- `src/components/spira/Targets.desktop.test.tsx`
- `src/components/spira/Targets.mobile.test.tsx`

### Backend unit/service-level test files (current)

- `backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/target/TargetServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/resource/ResourceServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/GoalServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/RealityServiceTest.java`
- `backend/src/test/java/com/spiramindscape/backend/goal/EntityTimestampTest.java`
- `backend/src/test/java/com/spiramindscape/backend/ai/crypto/EncryptionServiceTest.java` — AI key encryption (AES-256)
- `backend/src/test/java/com/spiramindscape/backend/ai/safety/SafetyServiceTest.java` — AI safety/boundary checks
- `backend/src/test/java/com/spiramindscape/backend/auth/AppUserServiceTest.java` — user creation/lookup on Google login
- `backend/src/test/java/com/spiramindscape/backend/auth/SessionSerializationTest.java` — Spring Session JDBC round-trip
- `backend/src/test/java/com/spiramindscape/backend/ai/grow/BookChunkerTest.java` — text chunking for GROW RAG
- `backend/src/test/java/com/spiramindscape/backend/ai/grow/GrowLibraryServiceTest.java` — book retrieval logic
- `backend/src/test/java/com/spiramindscape/backend/ai/grow/GoalMemoryServiceTest.java` — session memory persistence
- `backend/src/test/java/com/spiramindscape/backend/ai/chat/AiChatServiceGrowTest.java` — GROW session flow
- `backend/src/test/java/com/spiramindscape/backend/ai/key/AiKeyServiceTest.java` — BYOK key management
- `backend/src/test/java/com/spiramindscape/backend/ai/proposal/AiProposalServiceTest.java` — proposal lifecycle
- `backend/src/test/java/com/spiramindscape/backend/security/RateLimitFilterTest.java` — rate limiter
- `backend/src/test/java/com/spiramindscape/backend/config/CorsConfigTest.java` — CORS origin rules
- `backend/src/test/java/com/spiramindscape/backend/web/RestExceptionHandlerTest.java` — REST error envelope

### Backend GraphQL integration/contract test files (current)

- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalConfidenceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalListIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalIsolationIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCascadeDeleteIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/RealityIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/OptionIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java`
- `backend/src/test/java/com/spiramindscape/backend/graphql/CrossUserIsolationIntegrationTest.java` — verifies users can't see each other's goals
- `backend/src/test/java/com/spiramindscape/backend/graphql/SecurityIntegrationTest.java` — auth-gated endpoints, unauthenticated access
- `backend/src/test/java/com/spiramindscape/backend/graphql/E2eProfileAuthIntegrationTest.java` — `X-E2E-Auth` header login used by CI
- `backend/src/test/java/com/spiramindscape/backend/ai/AiKeySecurityIntegrationTest.java` — encrypted key never returned in plaintext

### Python E2E test files (current)

Black-box tests that drive a running backend over GraphQL against a real PostgreSQL
(no test profile / H2) — this is what production actually runs, so Flyway migrations
execute end-to-end.

- `tests-e2e/test_health.py`
- `tests-e2e/test_goals_e2e.py`
- `tests-e2e/test_targets_e2e.py`
- `tests-e2e/test_progress_e2e.py`
- `tests-e2e/test_reality_e2e.py`
- `tests-e2e/test_options_e2e.py`
- `tests-e2e/test_resources_e2e.py`
- `tests-e2e/test_error_envelope.py`

### Test structure and conventions 

- Most integration test classes are organized by domain area (goals/reality/options/targets/resources/confidence).
- Integration tests run with Spring Boot + GraphQL tester and test the full flow (GraphQL -> service -> persistence -> response).
- Many integration tests use AAA comments (`Arrange / Act / Assert`) and `@DisplayName`.
- Many error-path tests verify both message and error classification (for example `ValidationError` or `NOT_FOUND`).
- **Boundary tests bind to the production constant — no magic numbers.** A test for a
  length/size limit never hardcodes the number; it derives the boundary values and the
  expected message from the single source of truth (e.g. `GoalService.MAX_GOAL_TITLE_LENGTH`,
  `ResourceService.MAX_RESOURCE_LABEL_LENGTH`). If the limit changes, the tests follow
  automatically and can never silently disagree with production. Full rationale and the
  do/don't example are in `docs/testing-guide.md` → *Convention: bind boundary tests to the production constant*.

- `@DisplayName` and parameterized tests are both first-class patterns in this suite.
  - Parameterized tests (`@ParameterizedTest`) are used when one behavior must be validated across multiple inputs without duplicating test code.
  - This keeps tests shorter, keeps scenarios aligned, and improves failure diagnostics by showing exactly which input case failed.
  - Examples:
    - `backend/src/test/java/com/spiramindscape/backend/goal/GoalValidationTest.java` (`goalValidationCases`)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/GoalCreationIntegrationTest.java` (deadline/title boundary matrices)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/ResourceIntegrationTest.java` (resource-type validation matrices)
    - `backend/src/test/java/com/spiramindscape/backend/graphql/TargetIntegrationTest.java` (invalid deadline matrices)
  - Result of review: no additional immediate refactor to parameterized tests is required right now; current parameterization already covers repeated input matrices where it gives clear value.

- AAA comments (`Arrange / Act / Assert`) are used where they materially improve readability (especially multi-step integration flows with setup + mutation/query + grouped assertions).
  - In very short or fluent-chain tests, AAA headers are intentionally omitted to avoid visual noise when the structure is already obvious from the code.

- Cleanup strategy (`@BeforeEach` vs `@AfterEach`) differs intentionally by class setup style:
  - `@BeforeEach` cleanup is used when each test starts from a guaranteed empty state before building its own fixtures.
  - `@AfterEach` cleanup is used when tests rely on shared setup created in `@BeforeEach` and need guaranteed teardown after execution.
  - Some classes use both (`@BeforeEach` for deterministic fixture creation, `@AfterEach` for teardown), which is expected for integration tests touching persistence.

- Error assertions now follow the same principle: validate both classification and message fragment when checking expected failure paths.
  
### Testing stack

- JUnit 5
- Spring Boot Test (`@SpringBootTest`)
- Spring GraphQL Test (`GraphQlTester`, `@AutoConfigureGraphQlTester`)
- AssertJ
- Jakarta Bean Validation (`jakarta.validation`)
- Mockito (for unit/service tests, e.g. `TargetServiceTest`)
- Vitest (frontend tests)
- pytest + requests (Python E2E tests in `tests-e2e/`)
- JaCoCo (backend line-coverage report, summarized in CI)
- Allure (aggregated HTML report across backend + E2E)

### CI (GitHub Actions) for tests

Workflow file:

- `.github/workflows/ci.yml`

When it runs:

- on every `push`
- on every `pull_request`
- manually via `workflow_dispatch`
- every night by schedule (`0 3 * * *`, UTC)

What it runs (five jobs — the last, `deploy`, only on push to `main`):

1. **Frontend tests and build**
   - `npm ci`
   - `npm test`
   - `npm run build`
2. **Backend tests**
   - `cd backend && sh ./mvnw test`
   - prints a JaCoCo line-coverage summary
3. **Python E2E tests** (`needs: backend`)
   - spins up a real **PostgreSQL 16** service container
   - builds and starts the backend JAR against it under the `e2e` profile (Flyway
     migrations run end-to-end; the `e2e` profile enables the `X-E2E-Auth` test login so
     the auth-gated API is reachable without real Google OAuth)
   - waits for `/health` to respond, then runs `pytest tests-e2e/`
4. **Allure report** (`needs: backend, e2e`, runs `always`)
   - merges backend + E2E Allure results into one HTML report
5. **Deploy to Cloud Run** (`needs: frontend, backend, e2e`)
   - runs **only on push to `main`, only after all tests pass** — a red build never ships
   - authenticates to GCP keylessly via **Workload Identity Federation** (no stored key)
   - `gcloud run deploy spira --source .` rolls out a new revision (env vars + secrets
     persist on the service). Setup: [docs/deploy-gcp-cloud-run.md §11](docs/deploy-gcp-cloud-run.md#11-continuous-deployment-from-github-auto-deploy-on-push)

Artifacts produced:

   - `backend-jacoco-report` (HTML/CSV line-coverage report)
   - `backend-surefire-reports` (raw Maven test reports)
   - `backend-allure-results` / `e2e-allure-results` (raw Allure input files)
   - `allure-report` (generated HTML Allure report covering backend + E2E)

Allure generation:

- Uses `simple-elf/allure-report-action` pinned to commit `53ebb757a2097edc77c53ecef4d454fc2f2f774c` (`v1.13`).
- Backend tests write Allure results to `backend/target/allure-results`.

How to know tests passed even if Allure HTML report is unavailable:

1. Open the workflow run and check job conclusions:
   - `Frontend tests and build` must be **success**
   - `Backend tests` must be **success**
   - `Python E2E tests` must be **success**
2. Open backend job steps and verify `Run backend tests` is **success**.
3. Download `backend-surefire-reports` artifact and inspect XML results (`failures=\"0\"`, `errors=\"0\"`).

If Allure report job fails but frontend/backend jobs are green, test execution still passed; only report generation failed.

### Where to find the report in GitHub UI

1. Open repository -> **Actions** -> choose the latest **CI** run.
2. Open run page and go to **Artifacts**.
3. Download `allure-report`.
4. Unzip locally and open `index.html` from the extracted report folder.

### Other ways to check test status (without Allure report)

- **PR checks:** in the pull request, check that `Frontend tests and build` and `Backend tests` are green.
- **Workflow jobs:** in Actions run details, confirm `Run frontend tests` and `Run backend tests` steps are green.
- **Surefire artifact:** download `backend-surefire-reports` and verify XML counters (`failures=\"0\"`, `errors=\"0\"`, `skipped` as expected).
- **Raw backend logs:** backend job logs include Maven summary (`Tests run`, `Failures`, `Errors`, `Skipped`).

---

## Documentation

### `docs/` currently contains

Testing & tooling:

- `docs/testing-guide.md`
- `docs/frontend-testing-guide.md`
- `docs/unit-vs-integration-tests.md`
- `docs/test-coverage-report.md`
- `docs/test-audit-report.md`
- `docs/github-actions-ci.md`
- `docs/flyway-guide.md`
- `docs/graphiql-guide.md`
- `docs/linting-guide.md`
- `docs/deploy-oracle-vm.md`
- `docs/deploy-gcp-cloud-run.md`
- `docs/google-drive-integration-guide.md`

AI:

- `docs/ai-configuration.md`
- `docs/ai-integration.md`
- `docs/ai-testing.md`
- `docs/ai-mini-apps-plan.md`

### `specs/` currently contains

- `specs/mission.md`
- `specs/tech-stack.md`
- `specs/roadmap.md`
- `specs/2026-05-04-stabilize-frontend-mvp/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-06-production-backend-foundation/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-08-backend-test-coverage/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-08-frontend-backend-integration/{requirements.md,plan.md,validation.md}`
- `specs/2026-05-28-google-oauth-authentication/{requirements.md,plan.md,validation.md}`
- `specs/2026-06-07-ai-assistant-cards-and-drawers/` — AI proposal card/drawer UX design


