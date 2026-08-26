package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** RabbitMQ 中的稳定社区事件信封。 */
public record CommunityEventPayload(
        String eventId,
        String eventType,
        String aggregateId,
        String actorUserId,
        Instant occurredAt
) { }
