-- 第八周：权限即时失效、管理员审计、社区帖子、媒体和可靠事件 Outbox。
ALTER TABLE app_user
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 1 AFTER status;

CREATE TABLE admin_audit_log (
    id CHAR(36) NOT NULL,
    actor_user_id CHAR(36) NOT NULL,
    target_user_id CHAR(36) NULL,
    action VARCHAR(60) NOT NULL,
    before_value VARCHAR(500) NULL,
    after_value VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_admin_audit_created (created_at DESC),
    KEY idx_admin_audit_target (target_user_id, created_at DESC),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_admin_audit_target FOREIGN KEY (target_user_id) REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_topic (
    id CHAR(36) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(300) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_topic_name (name),
    CONSTRAINT chk_community_topic_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO community_topic (id, name, description) VALUES
    ('topic-feeding-0000-0000-000000000001', '科学喂养', '宠物饮食、营养与饮水经验'),
    ('topic-health-00000-0000-000000000002', '健康护理', '日常护理、疫苗与就医经验'),
    ('topic-daily-00000-0000-000000000003', '萌宠日常', '记录与分享宠物生活');

CREATE TABLE community_post (
    id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    pet_profile_id CHAR(36) NULL,
    topic_id CHAR(36) NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    region VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    favorite_count BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_community_post_author_updated (author_id, updated_at DESC),
    KEY idx_community_post_public (status, published_at DESC),
    KEY idx_community_post_topic_public (topic_id, status, published_at DESC),
    CONSTRAINT fk_community_post_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT fk_community_post_pet FOREIGN KEY (pet_profile_id) REFERENCES pet_profile (id) ON DELETE SET NULL,
    CONSTRAINT fk_community_post_topic FOREIGN KEY (topic_id) REFERENCES community_topic (id) ON DELETE SET NULL,
    CONSTRAINT chk_community_post_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_media (
    id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    post_id CHAR(36) NULL,
    object_key VARCHAR(600) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processing_status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_media_object (object_key),
    KEY idx_community_media_owner_created (owner_id, created_at DESC),
    KEY idx_community_media_post (post_id),
    CONSTRAINT fk_community_media_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_community_media_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE SET NULL,
    CONSTRAINT chk_community_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT chk_community_media_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT chk_community_media_processing CHECK (processing_status IN ('WAITING', 'PROCESSING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE integration_outbox (
    id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_integration_outbox_due (status, next_attempt_at, created_at),
    CONSTRAINT chk_integration_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
