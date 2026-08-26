package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 私信内容区的一条持久化消息。 */
public record DirectMessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderUsername,
        String senderDisplayName,
        String senderAvatarUrl,
        String recipientId,
        String clientMessageId,
        String content,
        boolean read,
        Instant createdAt
) {
}
