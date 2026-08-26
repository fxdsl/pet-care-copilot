package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 可展示的管理员审计记录。 */
public record AdminAuditResponse(
        String id,
        String actorUserId,
        String actorUsername,
        String targetUserId,
        String targetUsername,
        String action,
        String beforeValue,
        String afterValue,
        Instant createdAt
) {
}
