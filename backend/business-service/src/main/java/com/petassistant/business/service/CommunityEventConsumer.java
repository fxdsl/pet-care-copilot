package com.petassistant.business.service;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.mapper.CommunityGovernanceMapper;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 社区事件统一消费者。数据库事件认领与业务状态更新处于同一事务，RabbitMQ 重投不会重复执行。
 */
@Service
public class CommunityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommunityEventConsumer.class);
    private static final String CONSUMER_NAME = "community-event-consumer-v1";

    private final ObjectMapper objectMapper;
    private final CommunityMediaMapper mediaMapper;
    private final CommunityGovernanceMapper governanceMapper;
    private final CommunityGovernanceCacheService governanceCache;

    public CommunityEventConsumer(
            ObjectMapper objectMapper,
            CommunityMediaMapper mediaMapper,
            CommunityGovernanceMapper governanceMapper,
            CommunityGovernanceCacheService governanceCache
    ) {
        this.objectMapper = objectMapper;
        this.mediaMapper = mediaMapper;
        this.governanceMapper = governanceMapper;
        this.governanceCache = governanceCache;
    }

    @Transactional
    @RabbitListener(queues = "${app.community.rabbit-queue}", autoStartup = "${app.community.rabbit-listener-enabled:true}")
    public void consume(String json) throws Exception {
        CommunityEventPayload event = objectMapper.readValue(json, CommunityEventPayload.class);
        MDC.put("eventId", event.eventId());
        try {
            // INSERT IGNORE 返回 0 表示该消费者已经提交过相同 eventId，可以直接确认消息。
            if (governanceMapper.claimEvent(event.eventId(), CONSUMER_NAME, Instant.now()) == 0) return;

            switch (event.eventType()) {
                case "MEDIA_CONFIRMED" -> mediaMapper.markProcessingReady(event.aggregateId());
                case "COMMUNITY_REPOSTED", "COMMUNITY_REPOST_REMOVED" ->
                        governanceCache.synchronizeRepost(governanceMapper.findRepostById(event.aggregateId()));
                case "COMMUNITY_MUTE_ENABLED", "COMMUNITY_MUTE_DISABLED",
                     "COMMUNITY_BLOCK_ENABLED", "COMMUNITY_BLOCK_DISABLED" ->
                        governanceCache.synchronizeRelation(governanceMapper.findRelationById(event.aggregateId()));
                case "RECOMMENDATION_NOT_INTERESTED", "RECOMMENDATION_FEEDBACK_REVOKED" ->
                        governanceCache.synchronizeFeedback(governanceMapper.findFeedbackById(event.aggregateId()));
                default -> log.debug("Community event {} has no asynchronous projection", event.eventType());
            }
            log.info("Community event {} ({}) processed", event.eventId(), event.eventType());
        } finally {
            MDC.remove("eventId");
        }
    }
}
