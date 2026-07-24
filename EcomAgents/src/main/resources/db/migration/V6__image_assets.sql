CREATE TABLE image_assets (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES image_sessions(id),
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255),
    mime_type VARCHAR(100) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_image_assets_session_created
    ON image_assets (session_id, created_at)
    WHERE deleted_at IS NULL;
