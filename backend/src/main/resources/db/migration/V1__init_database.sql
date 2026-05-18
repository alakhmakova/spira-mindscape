-- Spira backend schema.
-- This phase intentionally has no authentication tables or user ownership.

CREATE TABLE goal (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    confidence_rating INTEGER NOT NULL CHECK (confidence_rating BETWEEN 1 AND 10),
    deadline VARCHAR(100),
    achieved_at VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reality_item (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('actions', 'obstacles')),
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE option (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE target (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('numeric', 'binary', 'checklist')),
    title VARCHAR(200) NOT NULL,
    start_value DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    total_value DOUBLE PRECISION,
    unit VARCHAR(50),
    done BOOLEAN NOT NULL DEFAULT FALSE,
    deadline VARCHAR(100),
    achieved_at VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE checklist_item (
    id BIGSERIAL PRIMARY KEY,
    target_id BIGINT NOT NULL REFERENCES target(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    deadline VARCHAR(100),
    achieved_at VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE resource (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('note', 'link', 'file', 'email')),
    title VARCHAR(20),
    body TEXT,
    url VARCHAR(1000),
    mime VARCHAR(100),
    data_url TEXT,
    name VARCHAR(20),
    role VARCHAR(200),
    email VARCHAR(200),
    phone VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reality_item_goal_id ON reality_item(goal_id);
CREATE INDEX idx_option_goal_id ON option(goal_id);
CREATE INDEX idx_target_goal_id ON target(goal_id);
CREATE INDEX idx_checklist_item_target_id ON checklist_item(target_id);
CREATE INDEX idx_resource_goal_id ON resource(goal_id);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_goal_updated_at
    BEFORE UPDATE ON goal
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reality_item_updated_at
    BEFORE UPDATE ON reality_item
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_option_updated_at
    BEFORE UPDATE ON option
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_target_updated_at
    BEFORE UPDATE ON target
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_checklist_item_updated_at
    BEFORE UPDATE ON checklist_item
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_resource_updated_at
    BEFORE UPDATE ON resource
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
