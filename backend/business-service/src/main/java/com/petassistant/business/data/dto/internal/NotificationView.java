package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 通知与触发者公开资料的 MyBatis 联合查询结果。 */
public record NotificationView(
        String id,
        String recipientId,
        String actorId,
        String actorUsername,
        String actorDisplayName,
        String actorAvatarUrl,
        String notificationType,
        String targetType,
        String targetId,
        String title,
        String content,
        Instant readAt,
        Instant createdAt
) {
}
