-- 第五周 PDF 导入：文档记录文件类型，分块保留可追踪的原始页码。
ALTER TABLE knowledge_document
    ADD COLUMN file_name VARCHAR(255) NULL AFTER source_name,
    ADD COLUMN document_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' AFTER file_name;

ALTER TABLE knowledge_chunk
    ADD COLUMN page_start INT NULL AFTER embedding_dimensions,
    ADD COLUMN page_end INT NULL AFTER page_start;

CREATE INDEX idx_knowledge_chunk_document_page
    ON knowledge_chunk (document_id, page_start, page_end);
