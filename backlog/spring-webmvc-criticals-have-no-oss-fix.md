# Two CRITICAL spring-webmvc CVEs have no open-source fix on the 6.2 line

- **ID:** BUG-097
- **Status:** 🐞 Open — suppressed in the scanner until 2026-12-31, not fixed
- **Reported by:** CI (dependency scan, 2026-10-08)
- **Area:** Backend (Spring Boot) + CI
- **Severity:** Low in practice (neither CVE is reachable here), but it blocks the CRITICAL gate

## Summary

The dependency scan started failing on every run — including the nightly `main` schedule — from
2026-10-06, on two Spring Framework advisories against `spring-webmvc 6.2.19` (pulled in by
Spring Boot **3.5.15**):

| CVE | What it is | Fixed in |
|---|---|---|
| **CVE-2026-47884** | `XsltView` path traversal → SSRF / RCE. CVSS 9.8 | **7.0.9** only |
| **CVE-2026-47890** | A bare CR in a rendered **view fragment** corrupts the SSE stream other users receive. Spring rates it **CVSS 2.6 (Low)**; the NVD feed Trivy reads calls it CRITICAL | **7.0.9** (OSS), 7.0.8.1 and 6.2.20 for **Enterprise Support subscribers only** |

There is no OSS patch to take: **6.2.19 is the last 6.2.x on Maven Central**, and the fix landed
on the 7.0 line — i.e. **Spring Boot 4**. The latest 3.5.x (`3.5.16`) still ships Framework 6.2.x,
so a routine patch bump cannot clear this.

**Neither CVE reaches this backend, and for the same reason: it renders no views at all.** There
is no Thymeleaf / FreeMarker / JSP dependency, no `ViewResolver`, no `ModelAndView` and no view
name anywhere in `backend/src/main` — every endpoint answers with JSON (GraphQL + REST) or a raw
SSE stream, and the SPA is served as a static asset.

- CVE-2026-47884 needs a `/` mapping that resolves a view whose name is not explicitly specified,
  so that the request path becomes the view path. There is no view resolution to reach, and
  `XsltView` is never instantiated.
- CVE-2026-47890 needs view **fragments** rendered into an SSE stream. The one SSE endpoint
  (`AiController.chat`, returning an `SseEmitter`) writes model text directly.

## Steps to reproduce

```
trivy fs --scanners vuln --severity CRITICAL --ignore-unfixed --offline-scan --exit-code 1 .
```

Without `--ignorefile .trivyignore.yaml` it reports `Total: 2 (CRITICAL: 2)` and exits 1 (verified
locally on Trivy 0.75.0, 2026-10-08). With it, 0.

## Root cause

An upstream advisory whose only remedy is a **major** framework upgrade, against a CRITICAL-blocks
policy. `--ignore-unfixed` does not help: Trivy marks both as `fixed` because a fixed version
*exists* — it just exists on a line we are not on.

## Fix approach

Two steps, and the first one is already done:

1. **Now (done, 2026-10-08):** both CVEs are suppressed in **`.trivyignore.yaml`**, each with a
   `statement` saying why it is unreachable and `expired_at: 2026-12-31`. The expiry is the point
   — once it passes the scan goes red again and the decision has to be made afresh rather than
   quietly inherited. The scan step passes `--ignorefile` explicitly, because the YAML ignore
   format is **not** auto-detected.
2. **The actual fix: migrate to Spring Boot 4 / Spring Framework 7.** That is a real piece of work
   (Spring Security 7, Spring GraphQL, the `RestClient`/`WebClient` changes, Flyway and the
   Jakarta baseline), not a version bump, and it should be planned on its own rather than rushed
   to clear a scanner. Java 17 already satisfies Boot 4's floor.

Watch for a 6.2.x OSS backport in the meantime — if one ever appears on Maven Central, taking it
is cheaper than either of the above and the suppression comes straight out.

## How to verify fixed

- `trivy fs --scanners vuln --severity CRITICAL --ignore-unfixed --offline-scan --exit-code 1 .`
  exits 0 **without** `--ignorefile`, and both entries are gone from `.trivyignore.yaml`.
- `backend/pom.xml` resolves `spring-webmvc` ≥ 7.0.9 (`./mvnw dependency:tree -Dincludes=org.springframework:spring-webmvc`).
- The backend test suite is green, and the app signs in, loads a goal and streams an AI reply.

## Resolution

Not resolved. Suppressed with an expiry; the Spring Boot 4 migration is still to be scheduled.
