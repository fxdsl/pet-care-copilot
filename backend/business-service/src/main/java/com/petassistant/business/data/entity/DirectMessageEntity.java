package com.petassistant.business.data.entity;

import java.time.Instant;

/** 私信事实实体；clientMessageId 用于处理浏览器重试产生的重复提交。 */
public record DirectMessageEntity(
        String id,
        String conversationId,
        String senderId,
        String recipientId,
        String clientMessageId,
        String content,
        Instant readAt,
        Instant createdAt
) {
}
