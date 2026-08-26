-- 第十周：站内通知、私信会话与离线消息事实表。
-- MySQL 保存最终事实；Redis 仅保存未读缓存、在线状态和可重放实时事件。
CREATE TABLE user_notification (
    id CHAR(36) NOT NULL,
    recipient_id CHAR(36) NOT NULL,
    actor_id CHAR(36) NULL,
    notification_type VARCHAR(30) NOT NULL,
    target_type VARCHAR(30) NULL,
    target_id CHAR(36) NULL,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    dedupe_key VARCHAR(180) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_dedupe (recipient_id, dedupe_key),
    KEY idx_notification_recipient_unread (recipient_id, read_at, created_at DESC),
    KEY idx_notification_recipient_type (recipient_id, notification_type, created_at DESC),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_notification_type CHECK (notification_type IN ('COMMENT', 'LIKE', 'FOLLOW', 'MODERATION', 'SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE direct_conversation (
    id CHAR(36) NOT NULL,
    participant_low_id CHAR(36) NOT NULL,
    participant_high_id CHAR(36) NOT NULL,
    last_message_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_direct_conversation_pair (participant_low_id, participant_high_id),
    KEY idx_direct_conversation_low_updated (participant_low_id, updated_at DESC),
    KEY idx_direct_conversation_high_updated (participant_high_id, updated_at DESC),
    CONSTRAINT fk_direct_conversation_low FOREIGN KEY (participant_low_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_conversation_high FOREIGN KEY (participant_high_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_direct_conversation_order CHECK (participant_low_id < participant_high_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE direct_message (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    sender_id CHAR(36) NOT NULL,
    recipient_id CHAR(36) NOT NULL,
    client_message_id CHAR(36) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_direct_message_client (sender_id, client_message_id),
    KEY idx_direct_message_conversation (conversation_id, created_at ASC),
    KEY idx_direct_message_recipient_unread (recipient_id, read_at, created_at DESC),
    CONSTRAINT fk_direct_message_conversation FOREIGN KEY (conversation_id) REFERENCES direct_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_message_sender FOREIGN KEY (sender_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_message_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_direct_message_not_self CHECK (sender_id != recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
