package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 前端审核时间线条目。 */
public record KnowledgeReviewRecordResponse(
        String id,
        int version,
        String reviewerUserId,
        String reviewerName,
        String action,
        String trustLevel,
        String reason,
        Instant createdAt
) { }
