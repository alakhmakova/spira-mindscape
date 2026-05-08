# Requirements: Frontend Backend Integration

## Scope

Connect the Spira frontend to the Spring Boot GraphQL backend and clean the frontend so it is ready to operate as the primary Spira application surface.

## Included

- Use the backend GraphQL API as the source of truth for goals, reality items, options, resources, and targets.
- Keep AI chat state local for now because AI persistence is outside the current backend scope.
- Preserve the existing Spira domain model and user workflows.
- Remove stale non-Spira scaffolding, branding, and comments from active source and package metadata.
- Remove obsolete external scaffold artifacts from the project.
- Keep frontend class names and app-specific utility names understandable and Spira-oriented.
- Add a basic backend sync state in the UI for loading and recoverable sync errors.
- Audit and improve basic accessibility for the main shell and goal overview:
  - searchable controls have accessible names;
  - icon-only buttons expose labels;
  - pressed/toggle controls expose state;
  - loading and sync errors use status semantics;
  - keyboard focus remains visible.
- Update README with the frontend-backend connection model and local stack instructions.

## Excluded

- Authentication and authorization.
- AI chat persistence.
- LocalStorage-to-backend import tooling.
- Full automated accessibility testing.
- Reordering persistence for options, because the backend schema does not yet expose an order field.
- Full offline mode.

## Success Criteria

- Frontend loads goals from `POST /graphql`.
- Goal CRUD operations call backend GraphQL mutations.
- Reality, options, resources, and targets write through to backend GraphQL mutations.
- The app remains usable during optimistic updates.
- The frontend build passes.
- Backend build/test still passes.
- No stale external scaffold names remain in active source, docs, package metadata, or project configuration.
- README explains how frontend requests reach the backend.

## Design Decisions

1. The frontend uses a small typed GraphQL client in `src/lib/spira/api.ts` instead of adding a generated GraphQL toolchain in this pass.
2. Zustand remains the UI state container so the current component surface can stay stable.
3. Backend data is loaded into Zustand at app startup.
4. Mutations are optimistic where the current UI expects immediate feedback.
5. Text-heavy updates are debounced before being sent to the backend.
6. The Vite dev server proxies `/graphql` to `http://localhost:8080`; production can override this with `VITE_GRAPHQL_ENDPOINT`.
