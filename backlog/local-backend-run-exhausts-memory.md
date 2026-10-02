# A local backend run takes ~1.5 GB and helps starve a 16 GB machine

- **ID:** BUG-089
- **Status:** ✅ Fixed
- **Reported by:** Claude Code, 2026-09-18 — the harness killed the background backend "because the
  system is running low on memory"; the owner asked what was taking the memory and whether 1.5 GB
  for the backend is normal
- **Area:** Backend local run (`backend/pom.xml` → `spring-boot-maven-plugin`), developer machine
- **Severity:** **Medium** — nothing in the product is affected, but the local stack (and the phone
  tunnel on top of it) went down mid-session, and lint / vitest had already been OOM-killed on the
  same machine (see the agent memory note on Stop-hook OOMs)

## Summary

`mvnw spring-boot:run` started the application JVM with **no heap limit**, so the JVM allowed itself
the default quarter of physical memory (~4 GB on a 16 GB laptop), grew into it lazily and never gave
it back. Together with the Maven launcher JVM it sat at **~1–1.5 GB**, on a machine that was also
running IntelliJ (2.3 GB with three AI-assistant plugins), Docker/WSL for Postgres (1.3 GB), Chrome,
Discord and an unused MySQL 8 service. Adding a Vite build, the test suites or a headless Chromium
for screenshots tipped it over.

The application does not need that much: production runs in a **512Mi** Cloud Run instance with
`-XX:MaxRAMPercentage=75.0` (`Dockerfile`), i.e. a ~384 MB heap.

## Steps to reproduce

1. Start Postgres, then `cd backend && .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.
2. Use the app for a while (open goals, chat), then check the `java.exe` running
   `BackendApplication` in Task Manager.
3. Observed: ~1–1.5 GB together with the Maven launcher, and it does not come down.

## Root cause

No `-Xmx` for the application JVM that `spring-boot:run` forks. The JVM's default maximum heap is
25 % of RAM, and the garbage collector prefers growing the heap to collecting it on a machine that
seems to have room.

## Fix approach

- Give `spring-boot:run` a heap cap in `backend/pom.xml`:
  `<jvmArguments>-Xmx768m</jvmArguments>` on `spring-boot-maven-plugin`. It applies to the local
  `spring-boot:run` only — the packaged jar and production are untouched (they keep the
  Dockerfile's `MaxRAMPercentage`). 768 MB is twice what production gives the same app.
- Machine side (done by the owner, 2026-09-19): removed the unused `MySQL80` Windows service
  (it auto-started with Windows, ~0.6 GB), Discord, and the IntelliJ AI-assistant plugins
  (Kilo Code, Copilot, Qoder).

**What the cap changes:** the garbage collector runs a little more often — sub-millisecond pauses,
not visible. The only risk is a single very heavy operation outgrowing 768 MB, which would fail in
production first, since production's limit is lower.

## How to verify fixed

1. Start the backend as above.
2. `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` — the `BackendApplication` process's
   command line contains `-Xmx768m`.
3. Its working set stays in the hundreds of MB (**304 MB** measured right after start-up on
   2026-09-19), not 1–1.5 GB.

## Resolution

Fixed 2026-09-19. `backend/pom.xml` — `jvmArguments -Xmx768m` on `spring-boot-maven-plugin`.
Verified: the application JVM started with `-Xmx768m` at 304 MB; the whole backend including the
Maven launcher at ~645 MB, down from ~1–1.5 GB; free RAM up from 3.3 GB to ~4 GB+ after the owner's
clean-up.

Not done, and optional: the Maven launcher JVM itself (~340 MB while it only waits) could be capped
with `backend/.mvn/jvm.config`, but that JVM also compiles the code, so a tight cap there risks
slower or failing builds for little gain.
