package com.petassistant.business.data.dto.internal;

import java.time.Instant;
import java.util.Map;

/** WebSocket、Redis Stream 与 Pub/Sub 共用的脱敏实时事件协议。 */
public record RealtimeEnvelope(
        String eventId,
        String recipientId,
        String type,
        Map<String, String> payload,
        Instant createdAt
) {
}
