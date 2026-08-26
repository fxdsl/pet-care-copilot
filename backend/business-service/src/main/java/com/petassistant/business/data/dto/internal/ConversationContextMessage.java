package com.petassistant.business.data.dto.internal;

/** Redis 与 FastAPI 共用的最小会话上下文，不缓存数据库主键等无关字段。 */
public record ConversationContextMessage(String role, String content) {
}
