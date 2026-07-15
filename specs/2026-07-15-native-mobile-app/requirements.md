# Native Mobile App + Cross-Device Sync — Requirements

## Overview

Spira is a single-origin web app (React SPA + Spring Boot GraphQL + PostgreSQL) with
Google-only auth and server-side sessions. This phase adds a **native Kotlin/Jetpack Compose
Android app** that reuses the existing GraphQL API, while keeping the web app fully intact.
Before the mobile work, it fixes a cross-device data-freshness bug so the same user sees
consistent data across desktop web, responsive mobile web, and the native app without
reloading or re-authenticating.

The guiding constraints (from the product owner): **keep the web version**, **free / cheapest
path**, **simple enough for an employer to review the work**, and **add new code rather than
rewrite existing code** wherever possible.

## Decisions (locked for this phase)

1. **Native stack = Kotlin + Jetpack Compose**, consuming the existing GraphQL API via
   **Apollo Kotlin**. The web and native UIs are separate codebases (styled twice); a shared
   design-token source limits drift.
2. **Sync is inherent, not rebuilt.** All surfaces use the same backend + per-user PostgreSQL
   data. The pre-mobile fix is client-side **refetch on return** (focus/visibility/resume +
   light poll), not realtime SSE (deferred).
3. **Auth for mobile is additive.** A new `POST /api/auth/google/mobile` endpoint verifies a
   Google ID token and reuses the existing user find-or-create + server-side session. The web
   OAuth flow, GraphQL schema, services, and entities are unchanged.
4. **Firebase scope = App Distribution + FCM + Crashlytics/Analytics** (no Hosting). The
   backend stays on Cloud Run; Firebase only distributes the app and provides push/telemetry.
5. **Distribution for review = web link first, plus the signed APK attached to a GitHub
   Release** for one-click download. Google Play ($25 one-time) is deferred.

## Goals

- Fix cross-device freshness on the web without a reload or re-login (Part 1).
- Add a mobile sign-in path that reuses existing identity + sessions (Part 2).
- Ship a native Android MVP: sign in, view goals, open a goal's five sections, update targets
  with low friction, stay fresh on resume (Part 3, Steps 1–6).
- Distribute the app via Firebase App Distribution; add Crashlytics/Analytics and FCM push.
- Keep governance guardrails: manual commits, Definition-of-Done loop, backlog (Part 0).

## Non-Goals

- No realtime SSE cross-device push in this phase (deferred).
- No Google Doc export on mobile in the MVP (the ID-token flow yields no Drive refresh token).
- No Google Play public listing yet.
- No rewrite of the web UI into a shared cross-platform framework — native Compose is separate.
- No change to the existing web OAuth flow, GraphQL schema, or domain entities.

## Constraints

- Backend remains on Cloud Run (free tier); no migration.
- New backend code is additive; existing behavior and tests must keep passing.
- Heavy tests (backend integration, Python E2E, Android emulator) run in GitHub Actions CI,
  not local commit-time hooks.
