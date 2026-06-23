-- ── Pinned Personal Tools ───────────────────────────────────────────────────
-- A pinned tool stays visible on the page as a floating window and reopens on
-- every load. Pinning is per-USER state (the tool already belongs to one user),
-- so it must live on the server — not in browser localStorage — to survive
-- logout/login and follow the user across devices (pin on desktop → visible on
-- phone). The window's pixel geometry stays device-local; only the pin does not.

ALTER TABLE tool_definitions
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;
