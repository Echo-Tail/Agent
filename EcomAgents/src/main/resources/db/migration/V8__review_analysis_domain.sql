CREATE TABLE review_analysis_projects (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT,
    name VARCHAR(200) NOT NULL,
    marketplace VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_review_projects_owner_updated
    ON review_analysis_projects (created_by, updated_at);
CREATE INDEX idx_review_projects_profile
    ON review_analysis_projects (profile_id);

CREATE TABLE review_project_products (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES review_analysis_projects(id) ON DELETE CASCADE,
    asin VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    product_name VARCHAR(300),
    review_limit INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_project_asin UNIQUE (project_id, asin),
    CONSTRAINT ck_review_product_role CHECK (role IN ('product', 'own', 'competitor')),
    CONSTRAINT ck_review_product_limit CHECK (review_limit BETWEEN 100 AND 500)
);

CREATE TABLE review_collection_batches (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES review_analysis_projects(id) ON DELETE CASCADE,
    bright_data_record_id BIGINT,
    snapshot_id VARCHAR(100),
    dataset_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_count INTEGER NOT NULL DEFAULT 0,
    collected_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_collection_idempotency UNIQUE (project_id, idempotency_key)
);

CREATE INDEX idx_review_collection_project_created
    ON review_collection_batches (project_id, created_at);
CREATE INDEX idx_review_collection_snapshot
    ON review_collection_batches (snapshot_id);

CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES review_analysis_projects(id) ON DELETE CASCADE,
    collection_batch_id BIGINT REFERENCES review_collection_batches(id),
    asin VARCHAR(20) NOT NULL,
    external_review_id VARCHAR(100),
    rating NUMERIC(2,1),
    title TEXT,
    review_text TEXT NOT NULL,
    review_date TIMESTAMP,
    verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    helpful_count INTEGER NOT NULL DEFAULT 0,
    reviewer_name VARCHAR(200),
    source_url TEXT,
    content_hash CHAR(64) NOT NULL,
    raw_json TEXT NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_product_review_hash UNIQUE (project_id, asin, content_hash),
    CONSTRAINT ck_product_review_rating CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);

CREATE UNIQUE INDEX uk_product_review_external
    ON product_reviews (project_id, asin, external_review_id)
    WHERE external_review_id IS NOT NULL;
CREATE INDEX idx_product_reviews_project_asin
    ON product_reviews (project_id, asin);
CREATE INDEX idx_product_reviews_project_rating
    ON product_reviews (project_id, rating);

CREATE TABLE review_analysis_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES review_analysis_projects(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    taxonomy_version VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    role_prompt TEXT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    model_id BIGINT REFERENCES ai_models(id),
    source_review_count INTEGER NOT NULL DEFAULT 0,
    processed_review_count INTEGER NOT NULL DEFAULT 0,
    failed_review_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    confirmed_by BIGINT,
    confirmed_at TIMESTAMP,
    error_message TEXT,
    CONSTRAINT uk_review_analysis_version UNIQUE (project_id, version_number),
    CONSTRAINT uk_review_analysis_idempotency UNIQUE (project_id, idempotency_key)
);

CREATE INDEX idx_review_analysis_run_project_created
    ON review_analysis_runs (project_id, created_at);

CREATE TABLE review_insights (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES review_analysis_runs(id) ON DELETE CASCADE,
    review_id BIGINT NOT NULL REFERENCES product_reviews(id) ON DELETE CASCADE,
    user_problem TEXT NOT NULL,
    usage_scenario VARCHAR(50) NOT NULL,
    product_module VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    sentiment VARCHAR(20) NOT NULL,
    evidence_quote TEXT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    improvement_action TEXT NOT NULL,
    return_risk SMALLINT NOT NULL,
    conversion_risk SMALLINT NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_review_insight_risks CHECK (
        return_risk BETWEEN 1 AND 5 AND conversion_risk BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_review_insight_confidence CHECK (confidence BETWEEN 0 AND 1)
);

CREATE INDEX idx_review_insights_run_dimensions
    ON review_insights (analysis_run_id, product_module, usage_scenario, severity);
CREATE INDEX idx_review_insights_review
    ON review_insights (review_id);

CREATE TABLE improvement_opportunities (
    id BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT NOT NULL REFERENCES review_analysis_runs(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    usage_scenario VARCHAR(50) NOT NULL,
    product_module VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    recommended_action TEXT NOT NULL,
    insight_count INTEGER NOT NULL DEFAULT 0,
    affected_review_ratio NUMERIC(6,5) NOT NULL DEFAULT 0,
    customer_impact NUMERIC(5,2) NOT NULL DEFAULT 0,
    business_impact NUMERIC(5,2) NOT NULL DEFAULT 0,
    implementation_effort NUMERIC(5,2) NOT NULL DEFAULT 40,
    priority_score NUMERIC(8,2) NOT NULL DEFAULT 0,
    rationale TEXT,
    manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_review_opportunities_run_priority
    ON improvement_opportunities (analysis_run_id, priority_score DESC);

CREATE TABLE review_opportunity_insights (
    id BIGSERIAL PRIMARY KEY,
    opportunity_id BIGINT NOT NULL REFERENCES improvement_opportunities(id) ON DELETE CASCADE,
    insight_id BIGINT NOT NULL REFERENCES review_insights(id) ON DELETE CASCADE,
    CONSTRAINT uk_review_opportunity_insight UNIQUE (opportunity_id, insight_id)
);

CREATE TABLE review_insight_audits (
    id BIGSERIAL PRIMARY KEY,
    insight_id BIGINT NOT NULL REFERENCES review_insights(id) ON DELETE CASCADE,
    reviewed_by BIGINT NOT NULL,
    evidence_valid BOOLEAN NOT NULL,
    module_accepted BOOLEAN NOT NULL,
    severity_accepted BOOLEAN NOT NULL,
    notes TEXT,
    reviewed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_review_insight_audit UNIQUE (insight_id, reviewed_by)
);

CREATE INDEX idx_review_insight_audits_insight ON review_insight_audits (insight_id);
