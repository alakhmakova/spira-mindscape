-- Widen resource labels from 20 to 200 characters.
-- The 20-char cap (V5) was too tight in practice: AI-generated and
-- user-written note titles / contact names routinely exceed it.
-- Widening is non-destructive — no existing data needs to change.
ALTER TABLE resource
    ALTER COLUMN title TYPE VARCHAR(200),
    ALTER COLUMN name TYPE VARCHAR(200);
