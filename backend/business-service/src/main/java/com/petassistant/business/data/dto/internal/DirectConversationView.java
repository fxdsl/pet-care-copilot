package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 会话列表投影，包含对方公开资料、最后一条消息和当前用户未读数。 */
public record DirectConversationView(
        String id,
        String otherUserId,
        String otherUsername,
        String otherDisplayName,
        String otherAvatarUrl,
        String lastMessageContent,
        String lastMessageSenderId,
        Instant lastMessageAt,
        long unreadCount,
        Instant createdAt,
        Instant updatedAt
) {
}
