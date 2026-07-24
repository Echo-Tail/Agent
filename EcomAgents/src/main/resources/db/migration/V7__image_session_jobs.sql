CREATE TABLE image_session_jobs (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES image_sessions(id),
    job_id BIGINT NOT NULL REFERENCES image_generation_jobs(id),
    operation VARCHAR(20) NOT NULL,
    parent_job_id BIGINT,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_image_session_job UNIQUE (job_id),
    CONSTRAINT uk_image_session_idempotency UNIQUE (session_id, idempotency_key)
);

CREATE INDEX idx_image_session_jobs_session_created
    ON image_session_jobs (session_id, created_at);
