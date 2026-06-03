-- V8: Add per-user ownership to goals.
-- IMPORTANT: existing rows are deleted first because the new column is NOT NULL.
-- This is acceptable: the database currently holds only dev/test data.

DELETE FROM goal;

ALTER TABLE goal
    ADD COLUMN user_id BIGINT NOT NULL
        REFERENCES app_user (id) ON DELETE CASCADE;

CREATE INDEX idx_goal_user_id ON goal (user_id);
