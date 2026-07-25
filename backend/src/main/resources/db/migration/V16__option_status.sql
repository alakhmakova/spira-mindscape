-- Options gain a mutually-exclusive status: 'none' | 'active' | 'good_idea' | 'didnt_work'.
-- The legacy boolean `selected` is kept in sync (selected == status = 'active') so the
-- Android client, which still reads/writes `selected`, keeps working unchanged.
ALTER TABLE option ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'none';

-- Backfill: any option that was selected becomes 'active'.
UPDATE option SET status = 'active' WHERE selected = true;
