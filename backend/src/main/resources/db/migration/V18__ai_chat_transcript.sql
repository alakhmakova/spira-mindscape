-- ── AI Chat Transcript ───────────────────────────────────────────────────────
-- Persists a user's regular-chat transcript per scope (one row per goal, plus a
-- global row) so the conversation SYNCS ACROSS DEVICES: start a chat on a phone,
-- open the assistant on a laptop, and the same history is there (last write wins).
-- GROW sessions are intentionally ephemeral and are NOT stored here.
--
-- `content` is the JSON array of chat messages the client renders (attachment
-- file bytes are stripped before storage — only names/labels are kept).
CREATE TABLE ai_chat_transcript (
    id          BIGSERIAL   PRIMARY KEY,
    app_user_id BIGINT      NOT NULL,
    goal_id     BIGINT      REFERENCES goal(id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One transcript per (user, goal), and exactly one global transcript per user.
-- Two partial unique indexes because a plain UNIQUE(app_user_id, goal_id) would
-- treat NULL goal_ids as distinct and allow many global rows per user.
CREATE UNIQUE INDEX ux_ai_chat_transcript_user_goal
    ON ai_chat_transcript (app_user_id, goal_id) WHERE goal_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ai_chat_transcript_user_global
    ON ai_chat_transcript (app_user_id) WHERE goal_id IS NULL;
