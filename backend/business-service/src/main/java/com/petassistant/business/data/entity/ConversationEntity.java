package com.petassistant.business.data.entity;

import java.time.Instant;

/**
 * 会话数据库实体，与 MySQL 的 {@code conversation} 表一一对应。
 * 实体只描述持久化数据，不承载缓存来源等接口运行时字段。
 */
public record ConversationEntity(
        String id,
        String userId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
