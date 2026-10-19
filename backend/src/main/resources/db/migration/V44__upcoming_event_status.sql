-- V44: an Upcoming event can be settled (owner, 2026-10-19).
--
-- `status` is the user's answer to the event itself, not to one of its reminders:
--   * `pending` — still ahead of the user; it stands in the Upcoming column and its reminders fire.
--   * `done`    — the user did it. It leaves Upcoming and stays on the timeline, ticked.
--   * `wont_do` — the user decided not to. It leaves Upcoming too, and the timeline shows a cross.
-- A settled event's reminders never fire again (ReminderSchedule.armed). `resolved_at` is when
-- the answer was given, which the timeline prints under the event.

ALTER TABLE upcoming_event
    ADD COLUMN status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD CONSTRAINT upcoming_event_status CHECK (status IN ('pending', 'done', 'wont_do'));
