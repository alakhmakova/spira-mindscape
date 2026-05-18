-- Add explicit position column to option table for user-defined ordering.
-- Existing rows get position = 0; application logic will assign correct values on first reorder.
ALTER TABLE option ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

-- Backfill: assign each option a unique position within its goal based on created_at order.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY goal_id ORDER BY created_at ASC) - 1 AS pos
    FROM option
)
UPDATE option
SET position = ranked.pos
FROM ranked
WHERE option.id = ranked.id;

CREATE INDEX idx_option_goal_id_position ON option(goal_id, position);
