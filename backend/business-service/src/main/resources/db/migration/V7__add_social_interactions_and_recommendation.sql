-- 第九周：社区互动、举报治理、地理推荐和打卡事实表。
ALTER TABLE community_post
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER region,
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER latitude,
    ADD KEY idx_community_post_region_public (region, status, published_at DESC);

CREATE TABLE community_comment (
    id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    parent_id CHAR(36) NULL,
    root_id CHAR(36) NULL,
    depth TINYINT NOT NULL DEFAULT 0,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    like_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_comment_post_created (post_id, created_at ASC),
    KEY idx_comment_root_created (root_id, created_at ASC),
    KEY idx_comment_author_created (author_id, created_at DESC),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES community_comment (id) ON DELETE SET NULL,
    CONSTRAINT fk_comment_root FOREIGN KEY (root_id) REFERENCES community_comment (id) ON DELETE SET NULL,
    CONSTRAINT chk_comment_depth CHECK (depth IN (0, 1)),
    CONSTRAINT chk_comment_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_post_like (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, user_id),
    KEY idx_post_like_user_created (user_id, created_at DESC),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_post_favorite (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (post_id, user_id),
    KEY idx_post_favorite_user_created (user_id, created_at DESC),
    CONSTRAINT fk_post_favorite_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_favorite_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_user_follow (
    follower_id CHAR(36) NOT NULL,
    followed_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (follower_id, followed_id),
    KEY idx_user_followed_created (followed_id, created_at DESC),
    CONSTRAINT fk_user_follow_follower FOREIGN KEY (follower_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_follow_followed FOREIGN KEY (followed_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_follow_not_self CHECK (follower_id != followed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_report (
    id CHAR(36) NOT NULL,
    reporter_id CHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id CHAR(36) NOT NULL,
    reason_type VARCHAR(30) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution VARCHAR(30) NULL,
    moderator_id CHAR(36) NULL,
    moderator_note VARCHAR(1000) NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_reporter_target (reporter_id, target_type, target_id),
    KEY idx_community_report_queue (status, created_at ASC),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES app_user (id),
    CONSTRAINT fk_report_moderator FOREIGN KEY (moderator_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_report_target CHECK (target_type IN ('POST', 'COMMENT', 'USER')),
    CONSTRAINT chk_report_reason CHECK (reason_type IN ('SPAM', 'ABUSE', 'MISINFORMATION', 'DANGEROUS_ADVICE', 'ILLEGAL_TRADE', 'PRIVACY', 'OTHER')),
    CONSTRAINT chk_report_status CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT chk_report_resolution CHECK (resolution IS NULL OR resolution IN ('NO_ACTION', 'HIDE_CONTENT', 'WARN_USER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_checkin (
    user_id CHAR(36) NOT NULL,
    checkin_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, checkin_date),
    KEY idx_community_checkin_date (checkin_date),
    CONSTRAINT fk_checkin_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
