-- 第三周基础 RAG：将本地向量以 JSON 保存，兼容当前 MySQL 8.0 Community。
-- 后续替换为专用向量数据库时，文档和分块主键仍可保持不变。
ALTER TABLE knowledge_chunk
    ADD COLUMN embedding_json JSON NULL AFTER token_estimate,
    ADD COLUMN embedding_model VARCHAR(100) NULL AFTER embedding_json,
    ADD COLUMN embedding_dimensions SMALLINT NULL AFTER embedding_model,
    ADD COLUMN embedded_at TIMESTAMP(6) NULL AFTER embedding_dimensions;

-- 该索引用于快速排除尚未完成向量化的旧分块。
CREATE INDEX idx_knowledge_chunk_embedding_model
    ON knowledge_chunk (embedding_model, document_id);
