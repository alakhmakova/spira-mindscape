-- Per-target progress lock.
--
-- Deliberately NULLABLE with no default: NULL means "not decided by the user", and the client
-- then treats an achieved target as locked and an unfinished one as unlocked. TRUE/FALSE record
-- an explicit choice (unlock a finished target to correct it; lock one that is still in progress).
ALTER TABLE target ADD COLUMN progress_locked boolean;
