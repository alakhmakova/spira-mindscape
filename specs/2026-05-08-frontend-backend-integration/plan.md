# Plan: Frontend Backend Integration

## Group 1: Close Previous Backend Foundation

1. Record backend foundation validation as complete.
2. Keep deferred backend work explicit: auth, tests, localStorage import, CI/CD, and AI persistence.

## Group 2: Frontend Cleanup

3. Remove stale non-Spira scaffolding and metadata.
4. Regenerate the npm lockfile from the current Vite SPA package manifest.
5. Remove non-Spira brand references from source comments and docs.
6. Keep class names that describe Spira app structure, such as `spira-main`, `spira-goals-grid`, and `spira-goal-workspace`.

## Group 3: Backend Data Connection

7. Add a typed GraphQL client for the current backend schema.
8. Map backend GraphQL shapes to the existing frontend domain union types.
9. Load goals from the backend when the app shell mounts.
10. Replace local-only goal state with backend-backed Zustand state.
11. Keep optimistic UI updates for responsive editing.
12. Debounce text and rich-content updates before sending them to the backend.

## Group 4: Accessibility Pass

13. Add accessible names to search fields and icon-only controls where missing.
14. Add pressed state to segmented controls.
15. Add status semantics for loading and sync error feedback.
16. Preserve visible keyboard focus from the global focus style.

## Group 5: Documentation

17. Update README so setup explains the Vite proxy and `VITE_GRAPHQL_ENDPOINT`.
18. Document the remaining known limitations.

## Group 6: Validation

19. Run frontend build.
20. Run backend test/build.
21. Search for removed brand/scaffold terms.
22. Smoke-test frontend and backend together through GraphQL.
