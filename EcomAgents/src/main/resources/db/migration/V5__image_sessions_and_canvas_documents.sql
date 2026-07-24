CREATE TABLE image_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    thumbnail_asset_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_image_sessions_user_updated
    ON image_sessions (user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE canvas_documents (
    session_id BIGINT PRIMARY KEY REFERENCES image_sessions(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0,
    schema_version INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
