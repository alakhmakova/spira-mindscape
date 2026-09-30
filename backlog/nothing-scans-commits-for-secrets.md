# BUG-095 — Nothing scans commits for secrets

**Status:** ✅ Fixed — with three open questions below, parked by the owner (2026-09-29) to come
back to
**Area:** Security / CI
**Severity:** High — the repository is public, so a committed secret is a published secret

## Summary

The repository had **no detective layer for secrets**: nothing looked at what was actually being
committed. What existed was preventive and name-based, and a secret pasted into a source file, a
test, a script or a Markdown note walked straight past all of it.

Raised 2026-09-29, after two independent reviews of the project's defences agreed on the gap.

## What was there before

| | State |
|---|---|
| `.gitignore` | `.env`, `.env.*` (keeping `*.example`), `*.local`, `.dev.vars`, `.wrangler/`, `android/app/google-services.json`, `.claude/` |
| Secrets in code | none — read from the environment (`${GOOGLE_CLIENT_SECRET}`, `${DATABASE_PASSWORD}`, `${AI_ENCRYPTION_KEY}`), Secret Manager in production |
| Deploy credentials | none to leak — GitHub → GCP is keyless (Workload Identity Federation) |
| Provider keys at rest | encrypted (`ai/crypto/EncryptionService.java`) |
| Secrets in logs | forbidden, and `LoggingConventionTest` fails the build — but that is the **runtime** log path, not git |
| **Pre-commit hooks** | **none.** `.git/hooks` held only `.sample` files, `core.hooksPath` unset, no husky/lefthook/pre-commit |
| **CI secret scan** | **none.** Trivy ran as `--scanners vuln` only; its `secret` scanner was never enabled, and there was no gitleaks/trufflehog step |
| GitHub secret scanning | **on**, with **push protection on** — but `non_provider_patterns` off |

So one real layer existed and it was GitHub's, at push time, for **known provider formats only**.
The likeliest leak for this project is not an Anthropic key — it is a **Neon connection string**
or `AI_ENCRYPTION_KEY` pasted while debugging, and GitHub recognises neither.

## Fix

Three layers, each covering the one before it. All of them read **one** rule file,
`.gitleaks.toml`, so the local check and CI cannot disagree about what a secret is.

1. **`.gitleaks.toml`** — gitleaks' default provider rules (`useDefault`) plus this project's own
   shapes: a Postgres/JDBC URL carrying a password, a Neon hostname, `AI_ENCRYPTION_KEY`,
   `GOOGLE_CLIENT_SECRET`, `DATABASE_PASSWORD`, and `google-services.json` by path. The allowlist
   holds only what is provably not a secret, each entry with its reason.
2. **`.githooks/pre-commit`** — scans the staged diff before the commit exists, so a secret never
   enters the local history either (a push rejected by GitHub still leaves it in your commits).
   Enabled per clone by `npm install` (`prepare` → `scripts/install-git-hooks.mjs`, which sets
   `core.hooksPath`) or `npm run setup:hooks`. Without gitleaks installed it says so and lets the
   commit through — a hook that blocks work over a missing tool gets disabled, and then nothing
   is checked at all.
3. **CI job `secret-scan`** — gitleaks over the **whole history** (`fetch-depth: 0`), blocking,
   with `--redact` so a finding is never printed into a public build log. **`deploy` now needs
   it**, so a leak stops the release rather than shipping with it.

`npm run scan:secrets` runs the same scan by hand. `docs/security-model.md` §11a documents all of
it, including the two traps found while writing the rules.

### One trap worth knowing

**Gitleaks treats the first capture group as the secret.** Written `(aws|azure|gcp)`, the Neon
rule reported the word "aws" as the finding: the report redacted three harmless characters, and
no allowlist entry for the hostname could ever match it. Every custom rule uses `(?:…)`.

## What the first scan found

219 commits, 11 MB, 9 findings. Eight were fixtures and documentation — the published dev-only
key (`spira-dev-only-key-32bytes-xyzz!` in base64), the test encryption key, a
`sk-ant-api03-super-secret-key-1234` in `EncryptionServiceTest`, `DATABASE_PASSWORD=change-me` in
the Oracle guide — all allowlisted by name with a reason.

**The ninth is real and is the owner's to decide:** `deploy.ps1` has carried the **production
Neon hostname** in the clear since 2026-06-04
(`ep-lingering-sky-….eu-central-1.aws.neon.tech`). It is not a credential — the password is a
Secret Manager reference (`spira-db-password:latest`), and that is correct — but on a public
repository it tells anyone exactly which host to attack. It is allowlisted **with that note**, so
the scan stays useful for new leaks; remove the allowlist line when the decision is made. The
options are to pass the host as a deploy variable like the password, or to accept it.

## Open questions — parked, to come back to

Three things the scanning work turned up that are **decisions or account settings, not code**.
None of them is blocking: the three layers above are in place and green without them. Recorded
here at the owner's instruction (2026-09-29) rather than done now.

### 1. Turn on GitHub's "non-provider patterns" (and validity checks)

Settings → Code security → Secret scanning. Today the repository has:

| Setting | State |
|---|---|
| `secret_scanning` | enabled |
| `secret_scanning_push_protection` | enabled |
| `secret_scanning_non_provider_patterns` | **disabled** |
| `secret_scanning_validity_checks` | **disabled** |

Push protection therefore refuses a known provider's key at push time but not a connection string
or this app's own key — which is the whole reason `.gitleaks.toml` exists. Turning the wider
patterns on would give a second, independent opinion at the moment of the push; validity checks
would say whether a leaked token is still live, which is what decides how fast rotation has to
happen.

**It has to be done in the UI.** A `PATCH` to `repos/:owner/:repo` with
`security_and_analysis.secret_scanning_non_provider_patterns.status=enabled` returned **200 with
the old value** — no error, nothing changed. The token carries `repo`, `workflow`, `read:org`,
`gist`. So either the setting needs a scope the CLI token has not got, or it is not writable
through that endpoint for this repository; a silent no-op either way, which is worth knowing
before anyone tries to script it again.

### 2. What to do about the Neon hostname in `deploy.ps1`

`deploy.ps1` has carried the production database host in the clear since 2026-06-04:

```
"DATABASE_URL=jdbc:postgresql://ep-lingering-sky-….eu-central-1.aws.neon.tech/neondb?sslmode=require"
```

It is **not** a credential — the password beside it is a Secret Manager reference
(`spira-db-password:latest`), which is the right way round. But on a public repository it names
the host to attack, and the password is then the only thing in the way. The options:

- pass the host the same way as the password (a deploy variable or a Secret Manager entry), which
  costs one line and removes the disclosure for good;
- accept it, on the grounds that the endpoint is useless without the password and Neon requires
  TLS.

Until it is decided, the hostname sits in `.gitleaks.toml`'s allowlist **with that reasoning on
the line**, so the scan stays green for genuinely new leaks. Delete that entry when the decision
is made — if the host moves into a secret, the rule should start catching it again.

### 3. Dependabot security updates are off

`dependabot_security_updates: disabled`. Nothing to do with secrets — it turned up while reading
the same settings page. CI already fails on CRITICAL advisories (`npm audit`, Trivy), so this
would only automate the pull requests that fix them.

## How to verify fixed

1. `npm run scan:secrets` → "no leaks found" on the current history (verified: 219 commits, 0).
2. Plant a secret and watch it fail — verified with six fabricated values, each caught by the rule
   meant for it: a Neon URL with a password, a bare JDBC URL with a password, a Neon host,
   `AI_ENCRYPTION_KEY=…`, `GOOGLE_CLIENT_SECRET=GOCSPX-…`, `DATABASE_PASSWORD=hunter2hunter2`.
   The defaults fire too (`anthropic-api-key`, `gcp-api-key`, `private-key`).
3. `git config --get core.hooksPath` → `.githooks` after `npm install`.
4. Push a branch: the `Secret scan` job runs, and `deploy` will not start without it.

## Resolution

Fixed 2026-09-29: `.gitleaks.toml`, `.githooks/pre-commit`, `scripts/install-git-hooks.mjs`,
`package.json` (`prepare`, `setup:hooks`, `scan:secrets`), `.github/workflows/ci.yml`
(`secret-scan`, and `deploy` needs it), `docs/security-model.md` §11a.

The defect itself — nothing looking at what is being committed — is closed. The three items under
**Open questions** are parked deliberately and are not part of it; this file stays the place they
are tracked until they are decided.
