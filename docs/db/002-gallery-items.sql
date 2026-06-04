-- ============================================================
-- 画廊 (Gallery) — V1 数据库迁移
-- PostgreSQL 语法
-- ============================================================

CREATE TABLE IF NOT EXISTS gallery_items (
    id              BIGSERIAL PRIMARY KEY,
    record_id       BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(200) DEFAULT '未命名作品',
    category_tags   VARCHAR(500),
    style_tags      VARCHAR(500),
    negative_prompt TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    view_count      INTEGER      DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_gallery_status ON gallery_items (status);
CREATE INDEX IF NOT EXISTS idx_gallery_user_id ON gallery_items (user_id);
CREATE INDEX IF NOT EXISTS idx_gallery_record_id ON gallery_items (record_id);

-- 清理已有重复数据（如果存在）：删除同一 record_id 下多余的 REMOVED_BY_USER 行，保留最新的
DELETE FROM gallery_items a USING gallery_items b
WHERE a.id < b.id AND a.record_id = b.record_id AND a.status = b.status;

-- 注意：不再设唯一约束 (record_id, status)。
-- 取消发布/下架直接 DELETE 行，应用层通过 existsByRecordIdAndStatus 防重复。
COMMENT ON TABLE gallery_items IS '画廊作品表';
COMMENT ON COLUMN gallery_items.id IS '主键';
COMMENT ON COLUMN gallery_items.record_id IS '关联的图片生成记录 ID';
COMMENT ON COLUMN gallery_items.user_id IS '发布者用户 ID';
COMMENT ON COLUMN gallery_items.title IS '作品标题';
COMMENT ON COLUMN gallery_items.category_tags IS '品类标签，逗号分隔';
COMMENT ON COLUMN gallery_items.style_tags IS '风格标签，逗号分隔';
COMMENT ON COLUMN gallery_items.negative_prompt IS '负面提示词';
COMMENT ON COLUMN gallery_items.status IS '状态: PUBLISHED / REMOVED_BY_USER / REMOVED_BY_ADMIN';
COMMENT ON COLUMN gallery_items.view_count IS '浏览次数';
COMMENT ON COLUMN gallery_items.created_at IS '创建时间';
COMMENT ON COLUMN gallery_items.updated_at IS '更新时间';
