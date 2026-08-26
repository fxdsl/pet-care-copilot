package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 举报提交和管理端审核的统一响应。 */
public record CommunityReportResponse(
        String id,
        String reporterId,
        String reporterUsername,
        String targetType,
        String targetId,
        String reasonType,
        String description,
        String status,
        String resolution,
        String moderatorId,
        String moderatorUsername,
        String moderatorNote,
        int version,
        Instant createdAt,
        Instant resolvedAt
) { }
