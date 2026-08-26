package com.petassistant.business.data.entity;

import java.time.Instant;

/** 两人私信会话实体，参与者按用户 ID 排序以保证一对用户只有一个会话。 */
public record DirectConversationEntity(
        String id,
        String participantLowId,
        String participantHighId,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt
) {
}
