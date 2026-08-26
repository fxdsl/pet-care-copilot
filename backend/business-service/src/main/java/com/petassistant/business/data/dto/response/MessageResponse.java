package com.petassistant.business.data.dto.response;

import java.time.Instant;

/**
 * 会话消息接口响应。
 */
public record MessageResponse(
        String id,
        String conversationId,
        String role,
        String content,
        String modelName,
        Integer tokenCount,
        Instant createdAt
) {
}
