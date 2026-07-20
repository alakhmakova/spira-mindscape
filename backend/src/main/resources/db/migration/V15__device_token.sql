-- V15: Per-user device tokens for Firebase Cloud Messaging (FCM) push notifications.
-- One row per registered device (Android app). The FCM registration token is globally
-- unique to an app install, so `token` is UNIQUE: if a device re-registers under a
-- different user (e.g. sign out → sign in as someone else), the row moves to the new
-- owner via upsert rather than duplicating.

CREATE TABLE device_token (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token        TEXT        NOT NULL,
    platform     TEXT        NOT NULL DEFAULT 'android',
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX idx_device_token_token ON device_token (token);
CREATE INDEX idx_device_token_user ON device_token (user_id);
