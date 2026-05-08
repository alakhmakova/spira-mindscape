# Validation: Frontend Backend Integration

## Automated Checks

- [ ] `npm run build`
- [ ] `npm run lint`
- [ ] `cd backend && .\mvnw.cmd test`
- [ ] Search confirms no stale external scaffold names remain.

## Runtime Checks

- [ ] Start PostgreSQL with `cd backend && docker compose up -d postgres`.
- [ ] Start backend with `cd backend && .\mvnw.cmd spring-boot:run`.
- [ ] Start frontend with `npm run dev`.
- [ ] Frontend loads goals through `/graphql`.
- [ ] Creating a goal in the frontend persists to PostgreSQL through the backend.
- [ ] Editing goal title, description, confidence, and deadline writes through to backend.
- [ ] Reality actions and obstacles can be added, edited, and removed.
- [ ] Options can be added, edited, selected, and removed.
- [ ] Targets can be added, edited, and removed.
- [ ] Resources can be added, edited, and removed.
- [ ] Reloading the browser keeps backend data.

## Accessibility Audit

- [ ] Main search inputs have accessible names.
- [ ] Icon-only controls have accessible labels.
- [ ] Segmented view controls expose selected state.
- [ ] Loading and sync errors are announced as status updates.
- [ ] Keyboard focus is visible on interactive controls.
- [ ] Core workflows are usable with keyboard navigation.

## Known Limitations

- Option drag-and-drop reorder remains local only until the backend adds persistent ordering.
- AI chat remains local only until AI persistence is implemented.
- There is no auth or user isolation yet.
- Field clearing is limited by current backend update semantics for nullable fields.
