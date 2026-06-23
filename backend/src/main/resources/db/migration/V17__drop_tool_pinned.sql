-- ── Remove the pinned-tools feature ─────────────────────────────────────────
-- The pin feature (V16) was removed: a forward migration drops the column
-- rather than editing/deleting V16, so this is safe whether or not V16 was
-- already applied (on a fresh DB V16 adds the column and V17 drops it).

ALTER TABLE tool_definitions
    DROP COLUMN IF EXISTS pinned;
