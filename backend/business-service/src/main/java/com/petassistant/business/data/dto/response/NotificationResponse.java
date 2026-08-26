package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 消息中心展示的一条站内通知。 */
public record NotificationResponse(
        String id,
        String actorId,
        String actorUsername,
        String actorDisplayName,
        String actorAvatarUrl,
        String type,
        String targetType,
        String targetId,
        String title,
        String content,
        boolean read,
        Instant createdAt
) {
}
