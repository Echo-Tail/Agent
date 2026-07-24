CREATE TABLE image_generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL REFERENCES ai_models(id) ON DELETE RESTRICT,
    retry_of_job_id BIGINT REFERENCES image_generation_jobs(id) ON DELETE SET NULL,
    mode VARCHAR(32) NOT NULL,
    prompt TEXT NOT NULL,
    negative_prompt TEXT,
    target_count INTEGER NOT NULL,
    options_json TEXT,
    provider VARCHAR(50) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    remote_model_name VARCHAR(100) NOT NULL,
    api_url VARCHAR(500) NOT NULL,
    capability VARCHAR(32) NOT NULL,
    credential_id BIGINT REFERENCES model_credentials(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL,
    execution_phase VARCHAR(32),
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    worker_id VARCHAR(100),
    lease_until TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    provider_task_token TEXT,
    provider_status VARCHAR(100),
    error_code VARCHAR(100),
    safe_error_message VARCHAR(500),
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_image_job_target_count CHECK (target_count BETWEEN 1 AND 10),
    CONSTRAINT ck_image_job_counts CHECK (success_count >= 0 AND failure_count >= 0),
    CONSTRAINT ck_image_job_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','PARTIALLY_SUCCEEDED','FAILED','CANCEL_REQUESTED','CANCELLED')),
    CONSTRAINT ck_image_job_phase CHECK (execution_phase IS NULL OR execution_phase IN ('PREPARING','SUBMITTING','POLLING','DOWNLOADING','PERSISTING'))
);

CREATE TABLE image_generation_job_inputs (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES image_generation_jobs(id) ON DELETE CASCADE,
    input_index INTEGER NOT NULL,
    role VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT,
    snapshot_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    CONSTRAINT uk_image_job_input UNIQUE (job_id, input_index),
    CONSTRAINT ck_image_job_input_index CHECK (input_index >= 0),
    CONSTRAINT ck_image_job_input_size CHECK (file_size > 0)
);

CREATE INDEX idx_image_job_claim
    ON image_generation_jobs (status, next_attempt_at, lease_until, created_at);
CREATE INDEX idx_image_job_user_created
    ON image_generation_jobs (user_id, created_at DESC);
CREATE INDEX idx_image_job_input_order
    ON image_generation_job_inputs (job_id, input_index);

ALTER TABLE IF EXISTS image_generation_records ADD COLUMN IF NOT EXISTS job_id BIGINT;
ALTER TABLE IF EXISTS image_generation_records ADD COLUMN IF NOT EXISTS output_index INTEGER;
ALTER TABLE IF EXISTS image_generation_records ADD COLUMN IF NOT EXISTS status VARCHAR(32);
ALTER TABLE IF EXISTS image_generation_records ADD COLUMN IF NOT EXISTS error_code VARCHAR(100);
ALTER TABLE IF EXISTS image_generation_records ADD COLUMN IF NOT EXISTS safe_error_message VARCHAR(500);
DO $$
BEGIN
    IF to_regclass('image_generation_records') IS NOT NULL THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uk_image_record_job_output '
             || 'ON image_generation_records (job_id, output_index) WHERE job_id IS NOT NULL';
    END IF;
END $$;
