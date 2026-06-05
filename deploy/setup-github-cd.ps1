# One-time setup for keyless CI/CD: lets GitHub Actions deploy to Cloud Run via
# Workload Identity Federation (WIF) — no service-account key stored in GitHub.
#
# Run ONCE, from the repo root, with gcloud already authenticated as a project Owner:
#     gcloud auth login
#     gcloud config set project <YOUR_PROJECT_ID>
#     .\deploy\setup-github-cd.ps1
#
# It is idempotent-ish: creating a pool/provider/SA that already exists prints a
# harmless "already exists" error you can ignore. At the end it prints the three
# GitHub repo Variables to set (and the `gh variable set` commands to do it).
#
# What it creates:
#   - a Workload Identity Pool + an OIDC provider trusting GitHub's token issuer,
#     restricted to THIS repo only (attribute-condition on assertion.repository);
#   - a dedicated deployer service account with the roles needed to build-from-source
#     and roll out a Cloud Run revision;
#   - an IAM binding letting the GitHub repo impersonate that SA.

$ErrorActionPreference = "Stop"

# ── Config ───────────────────────────────────────────────────────────────────
$Repo      = "alakhmakova/spira-mindscape"   # owner/repo that is allowed to deploy
$PoolId    = "github-pool"
$ProviderId = "github-provider"
$SaName    = "github-deployer"
$Region    = "europe-west1"                  # informational; deploy region is in ci.yml

# Derived from the active gcloud config.
$ProjectId  = (gcloud config get-value project).Trim()
if (-not $ProjectId -or $ProjectId -eq "(unset)") {
  throw "No active project. Run:  gcloud config set project <YOUR_PROJECT_ID>"
}
$ProjectNum = (gcloud projects describe $ProjectId --format="value(projectNumber)").Trim()
$SaEmail    = "$SaName@$ProjectId.iam.gserviceaccount.com"

Write-Host "Project:        $ProjectId ($ProjectNum)"
Write-Host "Repo allowed:   $Repo"
Write-Host "Deployer SA:    $SaEmail`n"

# ── 1. Enable the APIs WIF + source deploys need ─────────────────────────────
gcloud services enable `
  iamcredentials.googleapis.com `
  sts.googleapis.com `
  cloudbuild.googleapis.com `
  run.googleapis.com `
  artifactregistry.googleapis.com `
  --project $ProjectId

# ── 2. Workload Identity Pool + GitHub OIDC provider ─────────────────────────
gcloud iam workload-identity-pools create $PoolId `
  --project $ProjectId --location global `
  --display-name "GitHub Actions pool"

# The attribute-condition is the security boundary: ONLY tokens whose
# `repository` claim equals our repo can use this provider.
gcloud iam workload-identity-pools providers create-oidc $ProviderId `
  --project $ProjectId --location global --workload-identity-pool $PoolId `
  --display-name "GitHub provider" `
  --issuer-uri "https://token.actions.githubusercontent.com" `
  --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository" `
  --attribute-condition "assertion.repository == '$Repo'"

# ── 3. Dedicated deployer service account ────────────────────────────────────
gcloud iam service-accounts create $SaName `
  --project $ProjectId `
  --display-name "GitHub Actions Cloud Run deployer"

# Service-account creation is eventually consistent: granting a role to it
# immediately can fail with "Service account ... does not exist". Wait until it is
# actually describable before continuing.
Write-Host "Waiting for the service account to propagate..."
for ($i = 1; $i -le 12; $i++) {
  gcloud iam service-accounts describe $SaEmail --project $ProjectId 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { break }
  Start-Sleep -Seconds 5
}

# Roles the deployer needs to `gcloud run deploy --source .`:
#   run.admin              roll out Cloud Run revisions
#   cloudbuild.builds.editor  submit the Cloud Build that builds the Dockerfile
#   storage.admin          create/use the run-sources-* upload bucket
#   artifactregistry.writer push the built image
$roles = @(
  "roles/run.admin",
  "roles/cloudbuild.builds.editor",
  "roles/storage.admin",
  "roles/artifactregistry.writer"
)
foreach ($role in $roles) {
  gcloud projects add-iam-policy-binding $ProjectId `
    --member "serviceAccount:$SaEmail" --role $role --condition None | Out-Null
  # Report honestly: $LASTEXITCODE is the gcloud exit code, so a failed grant is
  # not silently reported as "granted".
  if ($LASTEXITCODE -eq 0) { Write-Host "  granted $role" }
  else { Write-Warning "  FAILED to grant $role (re-run the script to retry)" }
}

# The build/runtime run as the Compute Engine default SA; the deployer must be
# allowed to "act as" it (and as itself) to deploy.
$computeSa = "$ProjectNum-compute@developer.gserviceaccount.com"
gcloud iam service-accounts add-iam-policy-binding $computeSa `
  --project $ProjectId `
  --member "serviceAccount:$SaEmail" --role "roles/iam.serviceAccountUser" | Out-Null
Write-Host "  granted serviceAccountUser on $computeSa"

# ── 4. Let the GitHub repo impersonate the deployer SA ───────────────────────
$principal = "principalSet://iam.googleapis.com/projects/$ProjectNum/locations/global/workloadIdentityPools/$PoolId/attribute.repository/$Repo"
gcloud iam service-accounts add-iam-policy-binding $SaEmail `
  --project $ProjectId `
  --role "roles/iam.workloadIdentityUser" `
  --member $principal | Out-Null
Write-Host "  bound repo $Repo -> $SaEmail (workloadIdentityUser)`n"

# ── 5. Output the GitHub repo Variables to set ───────────────────────────────
$provider = "projects/$ProjectNum/locations/global/workloadIdentityPools/$PoolId/providers/$ProviderId"

Write-Host "============================================================"
Write-Host "Set these as GitHub repo Variables (Settings -> Secrets and"
Write-Host "variables -> Actions -> Variables), or run the commands below:"
Write-Host "============================================================"
Write-Host "  GCP_PROJECT_ID  = $ProjectId"
Write-Host "  GCP_WIF_PROVIDER = $provider"
Write-Host "  GCP_DEPLOY_SA   = $SaEmail`n"
Write-Host "gh variable set GCP_PROJECT_ID  --body `"$ProjectId`""
Write-Host "gh variable set GCP_WIF_PROVIDER --body `"$provider`""
Write-Host "gh variable set GCP_DEPLOY_SA   --body `"$SaEmail`""
Write-Host "`nDone. Push to main and the 'Deploy to Cloud Run' job will run after tests pass."
