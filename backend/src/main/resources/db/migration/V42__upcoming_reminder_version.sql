-- V42: an optimistic-lock version on upcoming_reminder.
--
-- The dispatcher reads every reminder, decides which have fired, and writes them back; a user can
-- answer one in between. Without a version the dispatcher's stale write put `answered_at` back to
-- NULL — the answer was lost and the reminder went off again — and two overlapping runs (a
-- scheduler retry over a slow run) could both push. With it, whichever writes second fails and
-- rolls back, and the next minute's run starts from what is really stored.
ALTER TABLE upcoming_reminder ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
