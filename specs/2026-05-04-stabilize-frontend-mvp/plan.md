# Plan: Stabilize Current Frontend MVP

---

## Group 1: Domain Model Cleanup

1. Remove target weighting from `src/lib/spira/types.ts`.
2. Update `src/lib/spira/progress.ts` so goal progress is a simple average of target progress.
3. Rename the email/person resource type from `contact` to `email` in the frontend type model.

---

## Group 2: Frontend Resource UI Alignment

4. Update resource UI logic to use the `email` resource type.
5. Keep user-facing labels as "Email".
6. Update empty-state and section hint copy from "contacts" to "emails".
7. Preserve existing Email resource behavior:
   - display name/email
   - copy email
   - mailto action
   - edit email resource

---

## Group 3: Local Prototype Persistence

8. Add a local persisted-state migration so existing browser data using old `contact` resources is converted to `email`.
9. Keep local Zustand persistence only as a temporary frontend prototype mechanism.

---

## Group 4: Constitution Spec

10. Create this feature spec under `specs/2026-05-04-stabilize-frontend-mvp/`.
11. Ensure requirements, plan, and validation reflect the constitution and current implementation.

---

## Group 5: Validation

12. Search the codebase for removed domain terms:
    - target weighting in domain/progress code
    - resource `contact` type
13. Run production build.
14. Manually verify the resource and target flows if running the app locally.
