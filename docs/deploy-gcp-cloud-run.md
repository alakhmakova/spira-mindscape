# Deploy to GCP Cloud Run + Neon (free tier)

A practical, beginner-friendly runbook for deploying Spira **for free** on Google
Cloud Run (backend container, scales to zero) with a Neon serverless PostgreSQL.
This is the cloud alternative to [`deploy-oracle-vm.md`](./deploy-oracle-vm.md).

> **Live deployment:** https://spira-952567559986.europe-west1.run.app
> (project `project-10702811-5962-4bf3-877`, region `europe-west1`). Redeploy with
> `.\deploy.ps1` from the repo root.

> **Why this shape?** Spira's auth uses **session cookies + CSRF** (Google OAuth).
> If the SPA and the API live on different domains, the browser won't send the
> session cookie cross-site and login breaks. So we deploy **one container that
> serves both the API and the built SPA** → a single origin → cookies/OAuth/CSRF
> all "just work". See [`ai-integration.md` §8](./ai-integration.md).

```
                 Google OAuth
                      ▲
                      │ (redirect)
   Browser  ◄───────► Cloud Run service  ───►  Neon PostgreSQL
   (one HTTPS origin) │  Spring Boot jar         (serverless, free)
                      │  ├── /api, /graphql, /oauth2, /login  → API
                      │  └── everything else                  → SPA index.html
```

---

## 0. Prerequisites

- A Google account + **Google Cloud project** (free; billing must be enabled, but
  the always-free tier won't charge within limits — set a budget alert, step 8).
- [`gcloud` CLI](https://cloud.google.com/sdk/docs/install) installed and logged in:
  ```powershell
  gcloud auth login
  gcloud projects list                       # find the right PROJECT_ID (see note)
  gcloud config set project YOUR_PROJECT_ID
  ```
  > **Pick the project that owns your OAuth client.** `gcloud projects list` shows
  > `PROJECT_ID`, `NAME`, and `PROJECT_NUMBER`. Your `GOOGLE_CLIENT_ID` starts with the
  > project **number** (e.g. `952567559986-…`) — choose the project whose `PROJECT_NUMBER`
  > matches it (where Drive API is enabled), **not** an auto-created "Default Gemini Project".
  >
  > `gcloud config set project` may print two **harmless warnings** — *"active project
  > does not match the quota project in your ADC"* and *"lacks an 'environment' tag"*.
  > Ignore both; `Updated property [core/project]` means it worked.
- **Billing must be enabled** on that project (Cloud Run / Cloud Build require it, even
  for the free tier — you won't be charged within limits). Check in Console → Billing.
- A free [Neon](https://neon.tech) account (the database — **do not** use Cloud SQL; it
  has no free tier and bills 24/7).
- The existing Google OAuth client (from local dev) — we'll add a production
  redirect URI to it.

---

## 1. One-time code changes (single-origin container) — ALREADY DONE

These are already implemented and committed in the repo. Listed here so you know
what makes the single-origin container work; you don't need to re-create them.

### 1a. Serve the SPA from Spring Boot + client-route fallback

The SPA is built into the jar (`classpath:/static/`) and served by Spring. Client
routes (e.g. `/goals/123`) fall back to `index.html` so a hard refresh doesn't 404.
Implemented as a `WebMvcConfigurer` with a custom `PathResourceResolver` (more robust
than regex route matching):

- [`backend/.../config/SpaStaticConfig.java`](../backend/src/main/java/com/spiramindscape/backend/config/SpaStaticConfig.java)
  — serves real static files; any other non-`/api`, non-`/graphql` path returns the
  SPA shell. Inert in local dev (Vite serves the SPA; `static/` is empty).
- [`SecurityConfig.java`](../backend/src/main/java/com/spiramindscape/backend/config/SecurityConfig.java)
  was updated so the SPA shell + static assets are **public** (anonymous users must
  be able to load the login page), while `/graphql` and `/api/**` stay authenticated.

### 1b. Honour the proxy's HTTPS scheme

Cloud Run terminates TLS and forwards plain HTTP to your container with
`X-Forwarded-Proto: https`. Without telling Spring, OAuth builds an **http://**
redirect URI (Google rejects it) and cookies aren't marked `Secure`. Add to
`application.properties`:

```properties
server.forward-headers-strategy=framework
# Mark the session cookie Secure in production (HTTPS only)
server.servlet.session.cookie.secure=${COOKIE_SECURE:false}
```

Set `COOKIE_SECURE=true` in the Cloud Run env (step 5); keep it `false` locally.

### 1c. A combined (multi-stage) Dockerfile

Already at the repo root — three files:

- [`Dockerfile`](../Dockerfile) — multi-stage **amd64** build: Stage 1 builds the SPA
  (`npm ci && npm run build`), Stage 2 builds the backend jar and copies the SPA into
  `src/main/resources/static/`, Stage 3 is a slim JRE running the jar. Listens on
  `$PORT` (Cloud Run injects it; `application.properties` reads `${PORT:8080}`), with
  `-XX:MaxRAMPercentage=75` to stay within the memory cap.
- [`.dockerignore`](../.dockerignore) — keeps the Docker build context small.
- [`.gcloudignore`](../.gcloudignore) — keeps the **Cloud Build upload** small for
  `gcloud run deploy --source .` (so `node_modules`/`target` aren't uploaded).

> Note: the existing `backend/Dockerfile` and `Dockerfile.frontend` are the **Oracle
> two-container** setup (arm64) — Cloud Run uses the root `Dockerfile` (amd64) instead.

---

## 2. Create the Neon database

1. In Neon, create a project (choose a region close to your Cloud Run region).
2. Copy the connection details. Neon gives a URL like:
   ```
   postgresql://USER:PASSWORD@ep-xxx-123.eu-central-1.aws.neon.tech/neondb?sslmode=require
   ```
3. Convert to the JDBC form Spira expects (host/db only in the URL; user/pass separate):
   - `DATABASE_URL = jdbc:postgresql://ep-xxx-123.eu-central-1.aws.neon.tech/neondb?sslmode=require`
   - `DATABASE_USERNAME = USER`
   - `DATABASE_PASSWORD = PASSWORD`

> **Two JDBC nuances:**
> - **Drop `channel_binding`** if Neon's string has it — it's a libpq param the JDBC
>   driver doesn't need. Keep only `?sslmode=require`.
> - **Use the direct host, not the `-pooler` one.** If the host contains `-pooler`
>   (Neon's PgBouncer endpoint), use the host **without** `-pooler` — the pooler breaks
>   server-side prepared statements (Flyway + Hikari). At `max-instances 1` the
>   connection count is tiny, so the direct endpoint is fine.

Flyway runs on startup and applies all migrations (`V1..V12`) to the empty Neon DB automatically.

> Neon free tier scales the DB to zero when idle; the first query after idle has a
> small wake-up latency (a second or two). Fine for a personal app.

---

## 3. Generate the production encryption key

AI keys are encrypted at rest with `AI_ENCRYPTION_KEY` (32 bytes, Base64). Never
use the dev default in production. Generate one:

```powershell
# 32 random bytes, Base64-encoded
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))
```

Keep this secret — losing/changing it makes already-stored keys undecryptable.

---

## 4. Store secrets in Secret Manager

First enable the APIs:
```powershell
gcloud services enable run.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com
```

> **PowerShell gotcha:** `echo -n` does **not** work in PowerShell — `echo` appends a
> newline and `-n` isn't a flag. A trailing newline corrupts the password / encryption
> key. Write each value with `WriteAllText` (no newline) to a temp file, then use it:

```powershell
# DB password (the part between ":" and "@" in the Neon string)
[IO.File]::WriteAllText("$env:TEMP\s.txt", 'PASTE_DB_PASSWORD')
gcloud secrets create spira-db-password --data-file="$env:TEMP\s.txt"

# Google OAuth client secret (Console → Credentials, next to the client id)
[IO.File]::WriteAllText("$env:TEMP\s.txt", 'PASTE_GOOGLE_CLIENT_SECRET')
gcloud secrets create spira-google-secret --data-file="$env:TEMP\s.txt"

# AI encryption key — generate (32 bytes Base64) and store
$key = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))
[IO.File]::WriteAllText("$env:TEMP\s.txt", $key)
gcloud secrets create spira-ai-key --data-file="$env:TEMP\s.txt"

Remove-Item "$env:TEMP\s.txt"
```

Then grant the **Compute Engine default service account** (`PROJECT_NUMBER-compute@developer.gserviceaccount.com`)
the roles it needs. ⚠️ This SA is used **both** as the Cloud Run runtime identity **and**
(on newer projects) as the **Cloud Build builder** for `gcloud run deploy --source`. If
it's missing build/deploy roles, the deploy fails with a **403 `storage.objects.get`** on
the `run-sources-…` bucket (see Gotchas). Grant all five up front:

```powershell
$proj = gcloud config get-value project
$num  = gcloud projects describe $proj --format="value(projectNumber)"
$sa   = "$num-compute@developer.gserviceaccount.com"

# read the secrets at runtime
gcloud projects add-iam-policy-binding $proj --member="serviceAccount:$sa" --role="roles/secretmanager.secretAccessor"
# build the image with Cloud Build (reads the source bucket, writes logs + Artifact Registry)
gcloud projects add-iam-policy-binding $proj --member="serviceAccount:$sa" --role="roles/cloudbuild.builds.builder"
# read the uploaded source zip (belt-and-suspenders for the 403 above)
gcloud projects add-iam-policy-binding $proj --member="serviceAccount:$sa" --role="roles/storage.objectViewer"
# deploy the new Cloud Run revision
gcloud projects add-iam-policy-binding $proj --member="serviceAccount:$sa" --role="roles/run.admin"
# let the build "act as" the runtime service account
gcloud projects add-iam-policy-binding $proj --member="serviceAccount:$sa" --role="roles/iam.serviceAccountUser"
```

> Each command prints `Updated IAM policy for project …`. IAM changes can take ~30–60 s to
> take effect — if the first deploy still 403s, wait a moment and retry.
>
> Don't be fooled: the legacy `PROJECT_NUMBER@cloudbuild.gserviceaccount.com` SA already
> has `cloudbuild.builds.builder`, but `--source` builds now run as the **compute** SA, so
> that's the one that needs the roles.

(Non-secret values like the DB URL/username and the Google client id are passed as plain
env vars in the deploy command.)

---

## 5. Deploy to Cloud Run (do this manual deploy first)

From the repo root. `--source .` uploads the working dir to Cloud Build, which builds
the root `Dockerfile`. The first deploy prints your service URL.

> **Deploy from source manually first; set up continuous deployment (§11) only after
> it succeeds.** This is the fast loop for catching build/config errors (IAM, Docker,
> ignore files) — you see the log instantly and re-run in seconds. Automate (CD) once
> the build is known-good. `--source .` uploads the **working dir** (uncommitted edits
> included), so you can fix and retry without committing each time.

> **Pass all env vars in ONE `--set-env-vars`** (comma-separated). Repeating the flag
> does **not** merge — only the last one is kept. Same for `--set-secrets`. None of our
> values contain commas, so a single comma-separated list is safe.

```powershell
gcloud run deploy spira `
  --source . `
  --region europe-west1 `
  --allow-unauthenticated `
  --memory 512Mi `
  --min-instances 0 `
  --max-instances 1 `
  --set-env-vars "DATABASE_URL=jdbc:postgresql://ep-xxx.eu-central-1.aws.neon.tech/neondb?sslmode=require,DATABASE_USERNAME=USER,GOOGLE_CLIENT_ID=952567559986-...apps.googleusercontent.com,COOKIE_SECURE=true" `
  --set-secrets "DATABASE_PASSWORD=spira-db-password:latest,GOOGLE_CLIENT_SECRET=spira-google-secret:latest,AI_ENCRYPTION_KEY=spira-ai-key:latest"
```

- `--allow-unauthenticated` = the *service* is publicly reachable; app-level access
  is still gated by Google OAuth inside the app.
- `--max-instances 1` is a cheap safety cap for a personal app (prevents surprise
  scale-out). Raise later if needed.
- `--min-instances 0` = scale to zero when idle (free). The trade-off is a cold
  start (~10–30 s for Spring Boot) on the first request after idle. To avoid cold
  starts you can set `--min-instances 1`, but that consumes free-tier seconds
  continuously and may incur cost — leave at 0 for free.

After it deploys, note the URL — for this project it's
`https://spira-952567559986.europe-west1.run.app`.

---

## 6. Wire up the public URL (OAuth + frontend URL)

Two values depend on the URL you just got, so set them **after** the first deploy.

1. **Google Cloud Console → APIs & Services → Credentials → your OAuth client:**
   add to **Authorized redirect URIs**:
   ```
   https://spira-952567559986.europe-west1.run.app/login/oauth2/code/google
   ```
   and to **Authorized JavaScript origins**:
   ```
   https://spira-952567559986.europe-west1.run.app
   ```
2. **Set `FRONTEND_URL`** to the same URL (used for the post-login redirect and CORS):
   ```powershell
   gcloud run services update spira --region europe-west1 `
     --update-env-vars "FRONTEND_URL=https://spira-952567559986.europe-west1.run.app" `
     --update-env-vars "CORS_ALLOWED_ORIGINS=https://spira-952567559986.europe-west1.run.app"
   ```

> Single origin means CORS isn't actually exercised by the SPA, but setting it
> correctly is harmless and future-proofs split-domain setups.

---

## 7. Verify

1. Open the service URL → the SPA loads, you're redirected to the login page.
2. Log in with Google → you land back in the app authenticated.
3. Create a goal (GraphQL mutation) → succeeds (session + CSRF working).
4. Open the AI panel → save a provider key → succeeds (the fix from §8 of
   `ai-integration.md`).
5. Check logs if anything fails:
   ```powershell
   gcloud run services logs read spira --region europe-west1 --limit 100
   ```

---

## 8. Stay free (cost guardrails)

- **Budget alert:** Billing → Budgets & alerts → create a budget (e.g. €1) with
  email alerts. Early warning if anything leaves the free tier.
- **Cloud Run always-free:** ~2M requests/month, 360k GiB-seconds, 180k vCPU-seconds.
  A personal app at `min-instances 0` stays well within this.
- **Neon free:** generous storage/compute with autosuspend. No card needed.
- **Cloud Build free:** 120 build-minutes/day — plenty for occasional deploys.
- Keep `--max-instances` low and `--min-instances 0`.

---

## 9. Common gotchas

| Symptom | Cause / fix |
|---|---|
| Deploy fails: `403 … does not have storage.objects.get access` on the `run-sources-…` bucket | The **compute** SA (used by `--source` builds) lacks build/deploy roles. Grant it `cloudbuild.builds.builder`, `storage.objectViewer`, `run.admin`, `iam.serviceAccountUser` (step 4). Wait ~30–60 s for IAM to propagate, then retry. The legacy `…@cloudbuild.gserviceaccount.com` having the builder role is **not** enough — `--source` runs as the compute SA. |
| Build fails: `./mvnw: unzip: not found` (exit 127) | The Maven wrapper needs `unzip` (absent in the base image). Fixed in the root `Dockerfile` by calling `mvn` directly instead of `./mvnw` (the `maven:…` base image already has Maven). |
| Compile error: `package com.spiramindscape.backend.target does not exist` | `.gcloudignore`/`.dockerignore` had a **bare `target`** line. In `.gcloudignore` (gitignore syntax) that matches *any* dir named `target` — including the source package `…/backend/target/` — so its sources weren't uploaded. Fix: ignore only the Maven output via the anchored `/backend/target`, never a bare `target`. |
| Container fails to start: Flyway/JDBC `Connection to localhost:5432 refused` | `DATABASE_URL` (and the secrets) weren't applied to the revision, so Spring fell back to the `localhost` default. Cause on Windows PowerShell: the long command **broke on paste** — backtick `` ` `` continuations (`Missing expression after unary operator '--'`) *and* even a single very long line wraps and splits (`--set-secrets: expected one argument`). **Robust fix: don't paste it — use the `deploy.ps1` script** (repo root): it builds the args from arrays (`-join ","`) and calls gcloud, so nothing splits. Run `.\deploy.ps1` (or `powershell -ExecutionPolicy Bypass -File .\deploy.ps1`). To fix env on an already-built image it uses `gcloud run services update … --set-env-vars … --set-secrets …`. |
| OAuth error `redirect_uri_mismatch` | The exact Cloud Run URL + `/login/oauth2/code/google` must be in Google's Authorized redirect URIs (step 6). |
| Login redirects to `http://` and fails | Missing `server.forward-headers-strategy=framework` (step 1b) — Spring didn't see the HTTPS proxy header. |
| Logged in but every POST is 403 | CSRF cookie not sent/echoed. With single origin it works; if you split domains, you need `SameSite=None; Secure` + cross-site fetch credentials. |
| `FlywayException` on first boot | Neon DB not empty or wrong `DATABASE_URL`. For a fresh deploy the DB should be empty; see [`flyway-guide.md`](./flyway-guide.md). |
| Cold start feels slow | Expected at `min-instances 0`. Set `--min-instances 1` to trade a little free-tier budget for warmth. |
| AI keys can't be decrypted after redeploy | `AI_ENCRYPTION_KEY` changed. It must stay constant across deploys. |
| Container fails to start, "port" error | Spring must listen on `$PORT`. `application.properties` already uses `server.port=${PORT:8080}`; don't hard-code the port. |

---

## 10. Redeploy (after the first time)

Once secrets and URL env vars are set, shipping a new version is one command:

```powershell
gcloud run deploy spira --source . --region europe-west1
```

Env vars/secrets persist between deploys unless you change them.

---

## 11. Continuous deployment from GitHub (auto-deploy on push)

`gcloud run deploy --source .` (§5) is a **manual** deploy — you run it each time.
Both that and continuous deployment build the **same root `Dockerfile`**; the only
difference is the trigger.

> ### ⚠️ Do the manual `--source` deploy FIRST, set up CD only after it succeeds
>
> **Get a green manual deploy before connecting the repo.** The first deploy is where
> all the real errors surface — and there are several (we hit each one): IAM roles on
> the compute SA (403 on the source bucket), the `unzip`/`mvnw` build issue, and the
> `.gcloudignore` excluding the `target` **source** package. With `gcloud run deploy
> --source .` you see the build log immediately, fix, and re-run in seconds.
>
> If you wire up continuous deployment first, every fix means **push → wait for the
> trigger → read Cloud Build logs in the console** — a far slower loop for shaking out
> these build/config errors. So: **iterate with `--source` until it deploys cleanly,
> then set up CD** (below) so future pushes auto-deploy a build you already know works.

**How it works:** Cloud Run sets up a **Cloud Build trigger** that watches a branch of
your GitHub repo. On each push to that branch → Cloud Build builds the `Dockerfile` →
deploys a new revision automatically.

**Roles you need** (ask your project admin if you're not the owner): Artifact Registry
Admin, Cloud Build Editor, Cloud Run Developer, Service Account User, Service Usage
Admin. The build's service account needs: Cloud Build Service Account, Cloud Run Admin,
Service Account User. (For a personal project where you're Owner, you already have these.)

**Set up (Console):**
1. Enable the Cloud Build API (done in §4).
2. Push the repo to GitHub (branch you want to deploy — e.g. `main`, or whichever holds
   the deploy code).
3. Console → **Cloud Run** → your `spira` service → **Set up continuous deployment**
   (or **Connect repo** on a new service).
4. Choose **Cloud Build** → authenticate the **Cloud Build GitHub app** → pick the
   `spira-mindscape` repo (use **Manage connected repositories** if it's not listed).
5. **Build configuration:**
   - **Branch:** the branch to deploy (e.g. `^main$`).
   - **Build type:** **Dockerfile**.
   - **Source location:** `/Dockerfile` (repo root — the one we added).
6. Save → finish the service config → the trigger is created and runs on the next push.

**Important — env vars & secrets still apply.** The trigger only builds + deploys; it
does **not** re-enter the `--set-env-vars` / `--set-secrets` from §5. Those are stored on
the **service** and persist across revisions, so set them once (via the first manual
deploy in §5, or in the Console service config). After that, pushes redeploy with the
same env/secrets.

> Tip: keep secrets in Secret Manager (§4) and reference them in the service — never put
> the DB password / client secret / encryption key into the repo or the Dockerfile.

---

*Pre-deploy gate (same as the Oracle runbook): run `npm test`, `npm run build`,
and `cd backend && ./mvnw test` — deploy only if all pass.*
