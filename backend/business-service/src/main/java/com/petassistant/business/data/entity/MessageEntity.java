package com.petassistant.business.data.entity;

import java.time.Instant;

/**
 * 消息数据库实体，与 MySQL 的 {@code message} 表一一对应。
 */
public record MessageEntity(
        String id,
        String conversationId,
        String role,
        String content,
        String modelName,
        Integer tokenCount,
        Instant createdAt
) {
}
