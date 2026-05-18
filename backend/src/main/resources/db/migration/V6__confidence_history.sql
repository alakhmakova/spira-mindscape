-- Confidence history table
CREATE TABLE confidence_history (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    confidence_rating INTEGER NOT NULL CHECK (confidence_rating BETWEEN 1 AND 10),
    at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_confidence_history_goal_id ON confidence_history(goal_id);
