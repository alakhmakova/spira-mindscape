-- V23: the All-goals page's Upcoming column — dates the user has chosen to keep an eye on.
--
-- Two shapes in one table, told apart by `kind`:
--   * `important` — a date of the user's own: a title and a date, nothing else.
--   * `goal` / `target` / `task` — a LINK to an existing goal, target or checklist item. Its date is
--     that item's deadline, read live, so editing the deadline anywhere moves the event with it.
--
-- Every link cascades: deleting the goal, the target or the checklist item removes the event, so
-- a link can never point at nothing.

CREATE TABLE upcoming_event (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind              VARCHAR(20)  NOT NULL,
    title             VARCHAR(200),
    event_date        TIMESTAMPTZ,
    goal_id           BIGINT       REFERENCES goal (id) ON DELETE CASCADE,
    target_id         BIGINT       REFERENCES target (id) ON DELETE CASCADE,
    checklist_item_id BIGINT       REFERENCES checklist_item (id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT upcoming_event_kind CHECK (kind IN ('important', 'goal', 'target', 'task'))
);

CREATE INDEX idx_upcoming_event_user ON upcoming_event (user_id);
CREATE INDEX idx_upcoming_event_goal ON upcoming_event (goal_id);
CREATE INDEX idx_upcoming_event_target ON upcoming_event (target_id);
CREATE INDEX idx_upcoming_event_checklist_item ON upcoming_event (checklist_item_id);
