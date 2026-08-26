-- 第十一周：知识投稿、人工审核、版本追踪和可撤回 RAG 发布。
CREATE TABLE knowledge_submission (
    id CHAR(36) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_business_id CHAR(36) NULL,
    author_user_id CHAR(36) NULL,
    title VARCHAR(300) NOT NULL,
    source_name VARCHAR(200) NULL,
    source_author VARCHAR(120) NULL,
    source_url VARCHAR(1000) NULL,
    file_name VARCHAR(255) NULL,
    document_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    pet_type VARCHAR(30) NOT NULL,
    category VARCHAR(50) NOT NULL,
    original_content LONGTEXT NOT NULL,
    cleaned_content LONGTEXT NULL,
    content_checksum CHAR(64) NULL,
    consent_status VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PRECHECKING',
    risk_level VARCHAR(20) NULL,
    risk_labels VARCHAR(1000) NULL,
    ai_summary VARCHAR(1000) NULL,
    quality_score DECIMAL(5,2) NULL,
    current_version INT NOT NULL DEFAULT 1,
    reviewer_user_id CHAR(36) NULL,
    reviewed_at TIMESTAMP(6) NULL,
    published_document_id CHAR(36) NULL,
    source_published_at TIMESTAMP(6) NULL,
    published_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_submission_source (source_type, source_business_id),
    KEY idx_knowledge_submission_author (author_user_id, updated_at DESC),
    KEY idx_knowledge_submission_review (status, risk_level, created_at),
    CONSTRAINT fk_knowledge_submission_author FOREIGN KEY (author_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_submission_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_knowledge_submission_source CHECK (source_type IN ('COMMUNITY_POST', 'ADMIN_UPLOAD')),
    CONSTRAINT chk_knowledge_submission_consent CHECK (consent_status IN ('GRANTED', 'NOT_REQUIRED', 'WITHDRAWN')),
    CONSTRAINT chk_knowledge_submission_status CHECK (status IN ('PRECHECKING', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'PUBLISHING', 'PUBLISHED', 'WITHDRAWN', 'FAILED')),
    CONSTRAINT chk_knowledge_submission_risk CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_submission_version (
    id CHAR(36) NOT NULL,
    submission_id CHAR(36) NOT NULL,
    version INT NOT NULL,
    title VARCHAR(300) NOT NULL,
    original_content LONGTEXT NOT NULL,
    cleaned_content LONGTEXT NULL,
    content_checksum CHAR(64) NULL,
    ai_summary VARCHAR(1000) NULL,
    risk_level VARCHAR(20) NULL,
    risk_labels VARCHAR(1000) NULL,
    quality_score DECIMAL(5,2) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_submission_version (submission_id, version),
    CONSTRAINT fk_knowledge_submission_version FOREIGN KEY (submission_id) REFERENCES knowledge_submission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_review_record (
    id CHAR(36) NOT NULL,
    submission_id CHAR(36) NOT NULL,
    version INT NOT NULL,
    reviewer_user_id CHAR(36) NULL,
    action VARCHAR(30) NOT NULL,
    trust_level CHAR(1) NULL,
    reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_knowledge_review_submission (submission_id, created_at),
    CONSTRAINT fk_knowledge_review_submission FOREIGN KEY (submission_id) REFERENCES knowledge_submission (id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_review_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_knowledge_review_action CHECK (action IN ('SUBMITTED', 'PRECHECK_COMPLETED', 'APPROVED', 'REJECTED', 'PUBLISHED', 'WITHDRAWN', 'FAILED')),
    CONSTRAINT chk_knowledge_review_trust CHECK (trust_level IS NULL OR trust_level IN ('A', 'B', 'C'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE knowledge_document
    ADD COLUMN submission_id CHAR(36) NULL AFTER id,
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'ADMIN_UPLOAD' AFTER title,
    ADD COLUMN source_business_id CHAR(36) NULL AFTER source_type,
    ADD COLUMN source_author VARCHAR(120) NULL AFTER source_name,
    ADD COLUMN author_user_id CHAR(36) NULL AFTER source_author,
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' AFTER status,
    ADD COLUMN trust_level CHAR(1) NOT NULL DEFAULT 'A' AFTER review_status,
    ADD COLUMN quality_score DECIMAL(5,2) NULL AFTER trust_level,
    ADD COLUMN consent_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' AFTER quality_score,
    ADD COLUMN reviewer_user_id CHAR(36) NULL AFTER consent_status,
    ADD COLUMN reviewed_at TIMESTAMP(6) NULL AFTER reviewer_user_id,
    ADD COLUMN version INT NOT NULL DEFAULT 1 AFTER reviewed_at,
    ADD COLUMN published_at TIMESTAMP(6) NULL AFTER version,
    ADD COLUMN expires_at TIMESTAMP(6) NULL AFTER published_at,
    ADD KEY idx_knowledge_document_submission (submission_id, version),
    ADD KEY idx_knowledge_document_retrievable (status, review_status, consent_status, expires_at),
    ADD CONSTRAINT fk_knowledge_document_submission FOREIGN KEY (submission_id) REFERENCES knowledge_submission (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_knowledge_document_author FOREIGN KEY (author_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_knowledge_document_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES app_user (id) ON DELETE SET NULL;

UPDATE knowledge_document
SET reviewed_at = created_at,
    published_at = created_at
WHERE reviewed_at IS NULL OR published_at IS NULL;

ALTER TABLE knowledge_submission
    ADD CONSTRAINT fk_knowledge_submission_document FOREIGN KEY (published_document_id) REFERENCES knowledge_document (id) ON DELETE SET NULL;
