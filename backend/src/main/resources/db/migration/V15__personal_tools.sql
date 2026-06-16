-- ── Personal Tools (AI mini-apps) ───────────────────────────────────────────
-- Small user-facing widgets (job tracker, weight log, countdown, …) the AI
-- assembles from approved UI primitives. A tool is a SCHEMA (definition) plus
-- RECORDS (the user's entered data). One generic renderer draws every tool.
--
-- JSON is stored as TEXT (not JSONB): the backend test suite runs on H2, which
-- has no JSONB type, and we never query inside the JSON — always whole-row by
-- id. See docs/ai-mini-apps-plan.md §3.

CREATE TABLE tool_definitions (
    id          BIGSERIAL    PRIMARY KEY,
    app_user_id BIGINT       NOT NULL,
    goal_id     BIGINT       REFERENCES goal(id) ON DELETE CASCADE,  -- NULL = global
    name        VARCHAR(120) NOT NULL,
    schema_json TEXT         NOT NULL,
    placement   VARCHAR(16)  NOT NULL,   -- 'goal' | 'all_goals' | 'tools'
    created_by  VARCHAR(8)   NOT NULL,   -- 'ai' | 'user'
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tool_def_user ON tool_definitions (app_user_id);
CREATE INDEX idx_tool_def_goal ON tool_definitions (goal_id);

CREATE TABLE tool_records (
    id          BIGSERIAL    PRIMARY KEY,
    tool_def_id BIGINT       NOT NULL REFERENCES tool_definitions(id) ON DELETE CASCADE,
    data_json   TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tool_record_def ON tool_records (tool_def_id);
