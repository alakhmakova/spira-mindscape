-- ── AI API Keys ──────────────────────────────────────────────────────────────
-- Stores per-user, per-provider API keys encrypted at rest with AES-256-GCM.
-- app_user_id is nullable until Google OAuth is merged into this branch;
-- the service layer defaults to a dev stub user (id = 1) when not authenticated.
CREATE TABLE ai_api_keys (
    id          BIGSERIAL    PRIMARY KEY,
    app_user_id BIGINT,
    provider    VARCHAR(32)  NOT NULL,
    model       VARCHAR(64),
    enc_key     TEXT         NOT NULL,
    key_hint    VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (app_user_id, provider)
);

-- ── AI Proposals ─────────────────────────────────────────────────────────────
-- Queues AI-generated changes that require explicit user approval before
-- being applied to goal data.
CREATE TABLE ai_proposals (
    id          BIGSERIAL    PRIMARY KEY,
    app_user_id BIGINT,
    goal_id     BIGINT       REFERENCES goal(id) ON DELETE CASCADE,
    type        VARCHAR(64)  NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Goal AI Memory ───────────────────────────────────────────────────────────
-- Living context block updated by the AI at the end of each GROW session.
ALTER TABLE goal ADD COLUMN ai_memory TEXT;
