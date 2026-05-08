-- Migrate deadline and achieved_at columns from VARCHAR to TIMESTAMPTZ.
-- Existing VARCHAR values are cast where possible; NULLs are preserved.

ALTER TABLE goal
    ALTER COLUMN deadline    TYPE TIMESTAMPTZ USING deadline::TIMESTAMPTZ,
    ALTER COLUMN achieved_at TYPE TIMESTAMPTZ USING achieved_at::TIMESTAMPTZ;

ALTER TABLE target
    ALTER COLUMN deadline    TYPE TIMESTAMPTZ USING deadline::TIMESTAMPTZ,
    ALTER COLUMN achieved_at TYPE TIMESTAMPTZ USING achieved_at::TIMESTAMPTZ;

ALTER TABLE checklist_item
    ALTER COLUMN deadline    TYPE TIMESTAMPTZ USING deadline::TIMESTAMPTZ,
    ALTER COLUMN achieved_at TYPE TIMESTAMPTZ USING achieved_at::TIMESTAMPTZ;
