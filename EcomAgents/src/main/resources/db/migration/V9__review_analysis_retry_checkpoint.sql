CREATE TABLE review_analysis_failures (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES review_analysis_runs(id) ON DELETE CASCADE,
    review_id BIGINT NOT NULL REFERENCES product_reviews(id) ON DELETE CASCADE,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    error_message TEXT NOT NULL,
    last_attempt_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_analysis_failure UNIQUE (analysis_run_id, review_id)
);

CREATE INDEX idx_review_analysis_failures_run
    ON review_analysis_failures (analysis_run_id, review_id);
