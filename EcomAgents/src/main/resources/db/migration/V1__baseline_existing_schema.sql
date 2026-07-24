-- Existing non-empty databases are stamped at version 1 by baseline-on-migrate.
-- A new database needs the legacy table referenced by V2 before Hibernate runs;
-- Hibernate continues adding the rest of the legacy schema during the transition.
CREATE TABLE IF NOT EXISTS ai_models (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(50),
    model_name VARCHAR(100) NOT NULL,
    api_url VARCHAR(500),
    api_key VARCHAR(500),
    api_type VARCHAR(20),
    api_version VARCHAR(50),
    max_tokens INTEGER,
    temperature DOUBLE PRECISION,
    is_default BOOLEAN,
    model_type VARCHAR(10),
    enabled BOOLEAN,
    created_at DATE NOT NULL,
    created_by BIGINT NOT NULL
);
