-- ── The LIVE GROW session ────────────────────────────────────────────────────
-- A coaching session in progress, so it survives a device rather than belonging
-- to one. It used to live only in the browser's localStorage (and, on Android,
-- only in the ViewModel): a session begun on the phone simply did not exist on
-- the laptop, and picking it up there meant starting again from nothing (owner,
-- 2026-09-08).
--
-- This is deliberately NOT the same row as `ai_chat_transcript`. That table holds
-- the ordinary chat and lives as long as the conversation does; a session row is
-- created when the session starts and DELETED the moment it ends — what outlives
-- it is the record the user chooses to keep (`goal.ai_memory`) and whatever they
-- approved into the goal.
--
-- `content` is the client's own JSON — the session's minutes, when it ends, and
-- its messages. The server does not read it: both surfaces write the same shape
-- and the schema does not have to move when that shape does.
CREATE TABLE ai_grow_session (
    id          BIGSERIAL   PRIMARY KEY,
    app_user_id BIGINT      NOT NULL,
    goal_id     BIGINT      NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live session per (user, goal). A second one would be two clocks running on
-- the same conversation, which is exactly what the sync exists to prevent.
CREATE UNIQUE INDEX ux_ai_grow_session_user_goal
    ON ai_grow_session (app_user_id, goal_id);
