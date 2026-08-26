package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 私信消息与发送者公开资料的查询投影。 */
public record DirectMessageView(
        String id,
        String conversationId,
        String senderId,
        String senderUsername,
        String senderDisplayName,
        String senderAvatarUrl,
        String recipientId,
        String clientMessageId,
        String content,
        Instant readAt,
        Instant createdAt
) {
}
