package com.petassistant.business.data.entity;

import java.time.Instant;

/** 举报数据库实体，version 用于管理员并发审核。 */
public record CommunityReportEntity(
        String id,
        String reporterId,
        String targetType,
        String targetId,
        String reasonType,
        String description,
        String status,
        String resolution,
        String moderatorId,
        String moderatorNote,
        int version,
        Instant createdAt,
        Instant resolvedAt
) { }
