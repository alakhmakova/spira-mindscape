# CI went red on newly published Tomcat CVEs, with no code change involved

- **ID:** BUG-082
- **Status:** ✅ Fixed
- **Reported by:** Owner, 2026-09-08 — "ещё я виде падение ci/cd - проверь в чем причина и исправь"
- **Area:** Build (`backend/pom.xml`), CI dependency scan
- **Severity:** Medium — nothing shipped was broken, but the pipeline was red and stayed red

## Summary

The nightly dependency scan started failing on **2026-09-03** with no commit behind it. Spring Boot
3.5.15 (and 3.5.16, the newest 3.5.x at the time) pins **Tomcat 10.1.55**, and three CRITICAL CVEs
against it were published that day: CVE-2026-65182 (security-constraint bypass), CVE-2026-65905
(authentication bypass) and CVE-2026-68525 (unauthorized access). All three are fixed in 10.1.58.

The scan gates on CRITICAL only, so those three were exactly enough to turn it red.

## Steps to reproduce

Run the dependency scan against `main` as of 2026-09-03 or later, before this fix.

## Root cause

A transitive version pinned by the parent, and a vulnerability disclosure — not a regression in this
repository. Worth stating plainly, because a red pipeline with a clean diff invites a hunt for a bug
that is not there.

## Fix approach

Override the parent's pin until the parent catches up, and take the HIGH findings in the same pass.

## How to verify fixed

`cd backend && ./mvnw.cmd dependency:tree` shows the versions below; the scan is green.

## Resolution

Fixed 2026-09-08 in `backend/pom.xml`.

- `<tomcat.version>10.1.59</tomcat.version>` — the newest 10.1.x, clearing all three CRITICALs.
- The seven HIGH findings the same scan reports, in the same pass. Two are parent properties
  (`netty.version` 4.1.135 → **4.1.136.Final**, `postgresql.version` 42.7.11 → **42.7.12**); two
  arrive through firebase-admin, which pins them itself, so each needed a `dependencyManagement`
  entry — `io.grpc:grpc-netty-shaded` 1.68.0 → **1.75.0** and
  `org.apache.httpcomponents.core5:httpcore5` (and `-h2`) 5.3.6 → **5.4.3**.
- Web side, `npm audit fix`: **nanoid** → 3.3.18, **postcss** → 8.5.12, **vite** → 7.3.5. No HIGH
  findings remain in the production tree.

Every override carries a comment saying what it patches and when to drop it. Backend 1064 tests and
web 273 tests green after the bumps. The user commits manually.
