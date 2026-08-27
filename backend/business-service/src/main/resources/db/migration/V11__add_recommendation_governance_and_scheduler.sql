-- 第十三周：转发、用户治理、推荐反馈、趋势快照与可审计定时任务。
ALTER TABLE community_post
    ADD COLUMN repost_count BIGINT NOT NULL DEFAULT 0 AFTER favorite_count;

CREATE TABLE community_post_repost (
    id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    quote_content VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_repost_user_post (user_id, post_id),
    KEY idx_community_repost_post_active (post_id, active, created_at DESC),
    CONSTRAINT fk_community_repost_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_repost_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_user_relation_control (
    id CHAR(36) NOT NULL,
    actor_user_id CHAR(36) NOT NULL,
    target_user_id CHAR(36) NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_relation_control (actor_user_id, target_user_id, relation_type),
    KEY idx_community_relation_target (target_user_id, relation_type, active),
    CONSTRAINT fk_community_relation_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_relation_target FOREIGN KEY (target_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_community_relation_not_self CHECK (actor_user_id != target_user_id),
    CONSTRAINT chk_community_relation_type CHECK (relation_type IN ('MUTE', 'BLOCK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_recommendation_feedback (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recommendation_feedback (user_id, post_id, feedback_type),
    KEY idx_recommendation_feedback_user_active (user_id, feedback_type, active),
    CONSTRAINT fk_recommendation_feedback_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_feedback_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT chk_recommendation_feedback_type CHECK (feedback_type IN ('NOT_INTERESTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_trend_snapshot (
    snapshot_at TIMESTAMP(6) NOT NULL,
    post_id CHAR(36) NOT NULL,
    rank_no INT NOT NULL,
    score DECIMAL(18, 6) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    PRIMARY KEY (snapshot_at, post_id),
    KEY idx_community_trend_rank (snapshot_at DESC, rank_no),
    CONSTRAINT fk_community_trend_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE scheduled_job_execution (
    id CHAR(36) NOT NULL,
    job_name VARCHAR(80) NOT NULL,
    batch_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    processed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    next_attempt_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scheduled_job_batch (job_name, batch_key),
    KEY idx_scheduled_job_status_due (status, next_attempt_at),
    CONSTRAINT chk_scheduled_job_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE processed_integration_event (
    event_id CHAR(36) NOT NULL,
    consumer_name VARCHAR(80) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 超过自动重试上限的事件不再无限循环，转入人工处理清单，原 Outbox 仍完整保留。
CREATE TABLE integration_event_manual_review (
    event_id CHAR(36) NOT NULL,
    reason VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    PRIMARY KEY (event_id),
    KEY idx_integration_manual_review_status (status, created_at),
    CONSTRAINT fk_integration_manual_review_event FOREIGN KEY (event_id) REFERENCES integration_outbox (id),
    CONSTRAINT chk_integration_manual_review_status CHECK (status IN ('PENDING', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 审计归档表不保留外键，避免历史账号状态变化破坏不可变审计记录。
CREATE TABLE admin_audit_log_archive (
    id CHAR(36) NOT NULL,
    actor_user_id CHAR(36) NOT NULL,
    target_user_id CHAR(36) NULL,
    action VARCHAR(60) NOT NULL,
    before_value VARCHAR(500) NULL,
    after_value VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    archived_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_archive_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
