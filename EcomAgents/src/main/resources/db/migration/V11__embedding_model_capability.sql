ALTER TABLE ai_model_capabilities
    DROP CONSTRAINT IF EXISTS ck_ai_model_capability;

ALTER TABLE ai_model_capabilities
    ADD CONSTRAINT ck_ai_model_capability
    CHECK (capability IN ('CHAT', 'EMBEDDING', 'TEXT_TO_IMAGE', 'IMAGE_TO_IMAGE'));
