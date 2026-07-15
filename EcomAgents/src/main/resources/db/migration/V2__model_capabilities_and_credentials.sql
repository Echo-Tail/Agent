CREATE TABLE model_credentials (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    encrypted_secret TEXT NOT NULL,
    encryption_version INTEGER NOT NULL DEFAULT 1,
    masked_hint VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_rotated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_model_capabilities (
    id BIGSERIAL PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    capability VARCHAR(32) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    model_name_override VARCHAR(100),
    api_url_override VARCHAR(500),
    credential_id_override BIGINT REFERENCES model_credentials(id) ON DELETE RESTRICT,
    options_json TEXT,
    CONSTRAINT uk_ai_model_capability UNIQUE (model_id, capability),
    CONSTRAINT ck_ai_model_capability CHECK (capability IN ('CHAT', 'TEXT_TO_IMAGE', 'IMAGE_TO_IMAGE'))
);

ALTER TABLE ai_models
    ADD COLUMN IF NOT EXISTS default_credential_id BIGINT REFERENCES model_credentials(id) ON DELETE RESTRICT;

CREATE INDEX idx_ai_model_capability_lookup
    ON ai_model_capabilities (model_id, capability);

INSERT INTO ai_model_capabilities (model_id, capability, protocol)
SELECT id, 'CHAT', 'OPENAI_CHAT' FROM ai_models WHERE model_type IN ('TEXT', 'MULTIMODAL')
ON CONFLICT (model_id, capability) DO NOTHING;

INSERT INTO ai_model_capabilities (model_id, capability, protocol)
SELECT id, capability,
       CASE WHEN LOWER(COALESCE(provider, '')) = 'qwen' THEN 'BAILIAN_IMAGE' ELSE 'OPENAI_IMAGE' END
FROM ai_models
CROSS JOIN (VALUES ('TEXT_TO_IMAGE'), ('IMAGE_TO_IMAGE')) AS capabilities(capability)
WHERE model_type IN ('IMAGE', 'MULTIMODAL')
ON CONFLICT (model_id, capability) DO NOTHING;
