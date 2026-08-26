package com.petassistant.business.service;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.entity.OutboxEventEntity;
import com.petassistant.business.data.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;

/** 在调用方业务事务中写入通用 Outbox，序列化失败会让业务一起回滚。 */
@Service
public class OutboxService {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(String aggregateType, String aggregateId, String eventType, String actorUserId) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        CommunityEventPayload payload = new CommunityEventPayload(
                eventId, eventType, aggregateId, actorUserId, now
        );
        try {
            mapper.insert(new OutboxEventEntity(
                    eventId, aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(payload), "PENDING", 0, now, now, null
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化业务事件", exception);
        }
    }
}
