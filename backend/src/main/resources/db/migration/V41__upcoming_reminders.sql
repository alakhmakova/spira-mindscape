-- V41: reminders for the Upcoming column (specs/2026-10-10-upcoming-notifications/plan.md).
--
--   * `upcoming_event.event_time` — an optional time of day for ANY Upcoming date. It belongs to
--     the Upcoming entry, not to the item: a linked goal's, target's or task's deadline stays a
--     day everywhere else, and this says "remind me about it as if it were at 14:00".
--   * `app_user.time_zone` — the IANA zone the client reports. "The day before at 09:00" means
--     nothing without one.
--   * `upcoming_reminder` — up to five per event. The moment a reminder fires is COMPUTED on every
--     run, never stored: a link's date is the item's deadline, read live, and a stored fire time
--     would go stale the moment that deadline moved. `last_fired_for` (the fire instant last sent)
--     is what makes the dispatcher idempotent; `answered_at` and `snoozed_until` are the in-app
--     "decide now" page's state.
--   * `web_push_subscription` — one browser's Web Push endpoint and keys.

ALTER TABLE upcoming_event ADD COLUMN event_time TIME;

ALTER TABLE app_user ADD COLUMN time_zone VARCHAR(64);

CREATE TABLE upcoming_reminder (
    id                BIGSERIAL    PRIMARY KEY,
    event_id          BIGINT       NOT NULL REFERENCES upcoming_event (id) ON DELETE CASCADE,
    user_id           BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- 0 means "on the day" (all-day) or "at the time" (timed).
    amount            INTEGER      NOT NULL,
    unit              VARCHAR(10)  NOT NULL,
    -- All-day events only: the time of day the reminder arrives, in minutes after midnight.
    at_minute_of_day  INTEGER,
    channel           VARCHAR(20)  NOT NULL,
    last_fired_for    TIMESTAMPTZ,
    answered_at       TIMESTAMPTZ,
    snoozed_until     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT upcoming_reminder_amount CHECK (amount BETWEEN 0 AND 999),
    CONSTRAINT upcoming_reminder_unit CHECK (unit IN ('minute', 'hour', 'day', 'week')),
    CONSTRAINT upcoming_reminder_minute CHECK (at_minute_of_day IS NULL
                                               OR at_minute_of_day BETWEEN 0 AND 1439),
    CONSTRAINT upcoming_reminder_channel CHECK (channel IN ('notification', 'email', 'fullscreen'))
);

CREATE INDEX idx_upcoming_reminder_event ON upcoming_reminder (event_id);
CREATE INDEX idx_upcoming_reminder_user ON upcoming_reminder (user_id);

CREATE TABLE web_push_subscription (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint    VARCHAR(1000) NOT NULL UNIQUE,
    p256dh      VARCHAR(200)  NOT NULL,
    auth        VARCHAR(100)  NOT NULL,
    user_agent  VARCHAR(300),
    created_at  TIMESTAMPTZ   NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_web_push_subscription_user ON web_push_subscription (user_id);
