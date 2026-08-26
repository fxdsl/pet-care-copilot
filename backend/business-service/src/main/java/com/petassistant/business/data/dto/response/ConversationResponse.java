package com.petassistant.business.data.dto.response;

import java.time.Instant;

/**
 * 会话接口响应，与数据库实体分离，避免表结构直接成为外部契约。
 */
public record ConversationResponse(
        String id,
        String userId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
