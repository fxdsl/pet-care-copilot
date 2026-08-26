-- 第十二周：统一搜索个人历史与 OpenSearch 全量重建任务事实。
CREATE TABLE search_history (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    query_text VARCHAR(120) NOT NULL,
    normalized_query VARCHAR(120) NOT NULL,
    query_hash CHAR(64) NOT NULL,
    filters_json VARCHAR(2000) NOT NULL,
    result_count BIGINT NOT NULL DEFAULT 0,
    search_count INT NOT NULL DEFAULT 1,
    last_searched_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_search_history_user_query (user_id, query_hash),
    KEY idx_search_history_user_recent (user_id, last_searched_at DESC),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_search_history_count CHECK (result_count >= 0 AND search_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE search_index_job (
    id CHAR(36) NOT NULL,
    requested_by CHAR(36) NULL,
    index_name VARCHAR(120) NOT NULL,
    index_version BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INT NOT NULL DEFAULT 0,
    indexed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_search_index_job_status_created (status, created_at DESC),
    CONSTRAINT fk_search_index_job_requester FOREIGN KEY (requested_by) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_search_index_job_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_search_index_job_counts CHECK (total_count >= 0 AND indexed_count >= 0 AND failed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
