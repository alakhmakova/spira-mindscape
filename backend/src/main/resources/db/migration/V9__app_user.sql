-- V7: Create the app_user table for Google-authenticated users.
-- Identity key is google_sub (stable OIDC sub claim), NOT email.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    google_sub    TEXT        NOT NULL,
    email         TEXT        NOT NULL,
    name          TEXT,
    picture_url   TEXT,
    role          TEXT        NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_app_user_google_sub ON app_user (google_sub);
CREATE UNIQUE INDEX idx_app_user_email      ON app_user (email);
