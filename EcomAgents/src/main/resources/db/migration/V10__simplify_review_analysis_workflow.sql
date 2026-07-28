ALTER TABLE review_analysis_projects
    ALTER COLUMN profile_id DROP NOT NULL;

ALTER TABLE review_project_products
    DROP CONSTRAINT IF EXISTS ck_review_product_role;

ALTER TABLE review_project_products
    ADD CONSTRAINT ck_review_product_role
    CHECK (role IN ('product', 'own', 'competitor'));
