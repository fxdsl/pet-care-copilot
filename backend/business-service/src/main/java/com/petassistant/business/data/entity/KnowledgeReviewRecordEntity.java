package com.petassistant.business.data.entity;

import java.time.Instant;

/** 知识投稿的追加式审核时间线；历史动作不会被后续状态覆盖。 */
public record KnowledgeReviewRecordEntity(
        String id,
        String submissionId,
        int version,
        String reviewerUserId,
        String action,
        String trustLevel,
        String reason,
        Instant createdAt
) { }
