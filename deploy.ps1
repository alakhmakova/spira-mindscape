# Spira → Cloud Run deploy helper. Run from the repo root:  .\deploy.ps1
#
# Why a script? Pasting the long gcloud command into PowerShell keeps breaking on
# line-wrap. Building the args here (no giant pasted line) avoids that entirely.
#
# Secrets stay in Secret Manager (referenced by name) — only non-sensitive config
# (Neon host, db user, OAuth client id) is here.

$ErrorActionPreference = "Stop"

$envVars = @(
  "DATABASE_URL=jdbc:postgresql://ep-lingering-sky-a2pgvtf4.eu-central-1.aws.neon.tech/neondb?sslmode=require",
  "DATABASE_USERNAME=neondb_owner",
  "GOOGLE_CLIENT_ID=952567559986-pv46g2sr17vltmsnbcoq5scdjbat2l3d.apps.googleusercontent.com",
  "COOKIE_SECURE=true"
) -join ","

$secrets = @(
  "DATABASE_PASSWORD=spira-db-password:latest",
  "GOOGLE_CLIENT_SECRET=spira-google-secret:latest",
  "AI_ENCRYPTION_KEY=spira-ai-key:latest"
) -join ","

# Fast path (the image is already built): apply env + secrets to the service.
gcloud run services update spira --region europe-west1 --set-env-vars $envVars --set-secrets $secrets

# --- Full rebuild + deploy (use after you change code) ---------------------------
# gcloud run deploy spira --source . --region europe-west1 --allow-unauthenticated `
#   --memory 512Mi --min-instances 0 --max-instances 1 `
#   --set-env-vars $envVars --set-secrets $secrets
#
# --- After you know the public URL (step 6): wire OAuth redirect ------------------
# $url = gcloud run services describe spira --region europe-west1 --format="value(status.url)"
# gcloud run services update spira --region europe-west1 --update-env-vars "FRONTEND_URL=$url"
# Then add  $url/login/oauth2/code/google  to the OAuth client's Authorized redirect URIs.
