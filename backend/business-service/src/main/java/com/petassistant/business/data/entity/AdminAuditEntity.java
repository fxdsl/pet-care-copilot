package com.petassistant.business.data.entity;

import java.time.Instant;

/** 管理员权限和账号状态变更审计记录。 */
public record AdminAuditEntity(
        String id,
        String actorUserId,
        String targetUserId,
        String action,
        String beforeValue,
        String afterValue,
        Instant createdAt
) {
}
