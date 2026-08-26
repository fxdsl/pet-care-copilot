package com.petassistant.business.data.entity;

import java.time.Instant;

/** 与业务数据同事务写入的 RabbitMQ 待发布事件。 */
public record OutboxEventEntity(
        String id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        String status,
        int attempts,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant publishedAt
) { }
