ALTER TABLE IF EXISTS amazon_image_tasks
    ADD COLUMN IF NOT EXISTS image_job_id BIGINT;

ALTER TABLE IF EXISTS amazon_image_results
    ADD COLUMN IF NOT EXISTS generation_record_id BIGINT;

DO $$
BEGIN
    IF to_regclass('amazon_image_tasks') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_amazon_image_tasks_image_job_id '
             || 'ON amazon_image_tasks (image_job_id)';
    END IF;
    IF to_regclass('amazon_image_results') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_amazon_image_results_generation_record_id '
             || 'ON amazon_image_results (generation_record_id)';
    END IF;
END $$;
