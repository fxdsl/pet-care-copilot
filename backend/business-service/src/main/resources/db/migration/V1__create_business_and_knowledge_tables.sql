CREATE TABLE app_user (
    id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversation (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_conversation_user_updated (user_id, updated_at DESC),
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE message (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model_name VARCHAR(100) NULL,
    token_count INT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_message_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT chk_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_document (
    id CHAR(36) NOT NULL,
    title VARCHAR(300) NOT NULL,
    source_url VARCHAR(1000) NULL,
    source_name VARCHAR(200) NULL,
    pet_type VARCHAR(30) NOT NULL,
    category VARCHAR(50) NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'zh-CN',
    content LONGTEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_checksum (content_checksum),
    KEY idx_knowledge_document_filter (pet_type, category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_chunk (
    id CHAR(36) NOT NULL,
    document_id CHAR(36) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    char_count INT NOT NULL,
    token_estimate INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_chunk_order (document_id, chunk_index),
    KEY idx_knowledge_chunk_document (document_id),
    CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE answer_feedback (
    id CHAR(36) NOT NULL,
    message_id CHAR(36) NOT NULL,
    rating TINYINT NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_answer_feedback_message (message_id),
    CONSTRAINT fk_answer_feedback_message FOREIGN KEY (message_id) REFERENCES message (id) ON DELETE CASCADE,
    CONSTRAINT chk_answer_feedback_rating CHECK (rating IN (-1, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
