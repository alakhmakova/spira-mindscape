# Deploy to GCP Cloud Run + Neon (free tier)

A practical, beginner-friendly runbook for deploying Spira **for free** on Google
Cloud Run (backend container, scales to zero) with a Neon serverless PostgreSQL.
This is the cloud alternative to [`deploy-oracle-vm.md`](./deploy-oracle-vm.md).

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
  gcloud config set project YOUR_PROJECT_ID
  ```
- A free [Neon](https://neon.tech) account.
- The existing Google OAuth client (from local dev) — we'll add a production
  redirect URI to it.

---

## 1. One-time code changes (single-origin container)

These are committed once; after that, deploys are just `gcloud run deploy`.

### 1a. Serve the SPA from Spring Boot + client-route fallback

The SPA is built to static files and served by Spring. Client-side routes (e.g.
`/goals/123`) must fall back to `index.html`, or a hard refresh 404s. Add a
forwarding controller that leaves the API paths alone:

```java
// backend/src/main/java/com/spiramindscape/backend/config/SpaForwardingController.java
package com.spiramindscape.backend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardingController {
    // Forward any non-file, non-API path to the SPA shell so the client router
    // can handle it. Excludes API/auth/static prefixes.
    @RequestMapping(value = {
        "/", "/{path:^(?!api|graphql|oauth2|login|assets|.*\\.).*$}",
        "/{path:^(?!api|graphql|oauth2|login|assets).*$}/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
```

> Tip: if the regex routing is fiddly with your Spring version, the robust
> alternative is a small `ErrorViewResolver`/`WebMvcConfigurer` that forwards 404s
> to `/index.html`. Verify locally before deploying.

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

Create `Dockerfile.cloudrun` at the repo root:

```dockerfile
# ---- Stage 1: build the SPA ----
FROM node:20-alpine AS frontend
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build            # outputs to /app/dist

# ---- Stage 2: build the backend jar (with the SPA inside it) ----
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
RUN cd backend && ./mvnw -B dependency:go-offline
COPY backend backend
# Copy built SPA into Spring's static resources so the jar serves it
COPY --from=frontend /app/dist/ backend/src/main/resources/static/
RUN cd backend && ./mvnw -B clean package -DskipTests

# ---- Stage 3: slim runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/backend/target/*.jar app.jar
# Cloud Run sets $PORT (default 8080); application.properties reads ${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

> `MaxRAMPercentage=75` keeps the JVM within Cloud Run's memory cap (step 5).

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

Flyway runs on startup and applies `V1..V10` to the empty Neon DB automatically.

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

## 4. Store secrets in Secret Manager (recommended)

```powershell
gcloud services enable secretmanager.googleapis.com run.googleapis.com cloudbuild.googleapis.com

echo -n "PASTE_DB_PASSWORD"      | gcloud secrets create spira-db-password   --data-file=-
echo -n "PASTE_GOOGLE_SECRET"    | gcloud secrets create spira-google-secret --data-file=-
echo -n "PASTE_AI_ENCRYPTION_KEY"| gcloud secrets create spira-ai-key        --data-file=-
```

(Non-secret values like the DB URL/username and the Google client id can be plain
env vars.)

---

## 5. Deploy to Cloud Run

From the repo root. `--source .` lets Cloud Build build `Dockerfile.cloudrun`
(rename it to `Dockerfile`, or pass a Cloud Build config; simplest is to keep one
Dockerfile). The first deploy will print your service URL.

```powershell
gcloud run deploy spira `
  --source . `
  --region europe-west1 `
  --allow-unauthenticated `
  --memory 512Mi `
  --min-instances 0 `
  --max-instances 1 `
  --set-env-vars "DATABASE_URL=jdbc:postgresql://ep-xxx.eu-central-1.aws.neon.tech/neondb?sslmode=require" `
  --set-env-vars "DATABASE_USERNAME=USER" `
  --set-env-vars "GOOGLE_CLIENT_ID=952567559986-...apps.googleusercontent.com" `
  --set-env-vars "COOKIE_SECURE=true" `
  --set-secrets "DATABASE_PASSWORD=spira-db-password:latest" `
  --set-secrets "GOOGLE_CLIENT_SECRET=spira-google-secret:latest" `
  --set-secrets "AI_ENCRYPTION_KEY=spira-ai-key:latest"
```

- `--allow-unauthenticated` = the *service* is publicly reachable; app-level access
  is still gated by Google OAuth inside the app.
- `--max-instances 1` is a cheap safety cap for a personal app (prevents surprise
  scale-out). Raise later if needed.
- `--min-instances 0` = scale to zero when idle (free). The trade-off is a cold
  start (~10–30 s for Spring Boot) on the first request after idle. To avoid cold
  starts you can set `--min-instances 1`, but that consumes free-tier seconds
  continuously and may incur cost — leave at 0 for free.

After it deploys, note the URL, e.g. `https://spira-abc123-ew.a.run.app`.

---

## 6. Wire up the public URL (OAuth + frontend URL)

Two values depend on the URL you just got, so set them **after** the first deploy.

1. **Google Cloud Console → APIs & Services → Credentials → your OAuth client:**
   add to **Authorized redirect URIs**:
   ```
   https://spira-abc123-ew.a.run.app/login/oauth2/code/google
   ```
   and to **Authorized JavaScript origins**:
   ```
   https://spira-abc123-ew.a.run.app
   ```
2. **Re-deploy with `FRONTEND_URL`** set to the same URL (used for the post-login
   redirect and CORS):
   ```powershell
   gcloud run services update spira --region europe-west1 `
     --update-env-vars "FRONTEND_URL=https://spira-abc123-ew.a.run.app" `
     --update-env-vars "CORS_ALLOWED_ORIGINS=https://spira-abc123-ew.a.run.app"
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

*Pre-deploy gate (same as the Oracle runbook): run `npm test`, `npm run build`,
and `cd backend && ./mvnw test` — deploy only if all pass.*
