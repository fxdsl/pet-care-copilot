package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 私信双栏左侧的一条会话摘要。 */
public record DirectConversationResponse(
        String id,
        String otherUserId,
        String otherUsername,
        String otherDisplayName,
        String otherAvatarUrl,
        String lastMessageContent,
        boolean lastMessageMine,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
