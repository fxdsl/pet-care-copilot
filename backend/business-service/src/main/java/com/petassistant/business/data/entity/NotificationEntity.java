package com.petassistant.business.data.entity;

import java.time.Instant;

/** 站内通知事实实体；Redis 未读数可以删除并由本表重建。 */
public record NotificationEntity(
        String id,
        String recipientId,
        String actorId,
        String notificationType,
        String targetType,
        String targetId,
        String title,
        String content,
        String dedupeKey,
        Instant readAt,
        Instant createdAt
) {
}
