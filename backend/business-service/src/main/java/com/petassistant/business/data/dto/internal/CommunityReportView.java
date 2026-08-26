package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 举报与举报人、处理人联表后的管理端投影。 */
public record CommunityReportView(
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
