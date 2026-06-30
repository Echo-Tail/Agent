-- PgVector 知识库向量表
-- 需先启用 pgvector 扩展: CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    chunk_text TEXT NOT NULL,
    embedding VECTOR(1536),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引加速余弦相似度检索
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_kb_id ON knowledge_embeddings(kb_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_hnsw ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops);
