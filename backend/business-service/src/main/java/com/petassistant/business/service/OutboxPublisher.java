package com.petassistant.business.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.petassistant.business.data.entity.OutboxEventEntity;
import com.petassistant.business.data.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定时认领 Outbox 并投递 RabbitMQ；失败采用指数退避，下次继续发布。 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventMapper mapper;
    private final ReliableRabbitPublisher rabbitPublisher;
    private final String communityExchange;
    private final String communityRoutingKey;
    private final String knowledgeExchange;
    private final String knowledgeRoutingKey;
    private final String searchExchange;
    private final String searchRoutingKey;
    private final PlatformMetricsService metrics;

    public OutboxPublisher(
            OutboxEventMapper mapper,
            ReliableRabbitPublisher rabbitPublisher,
            @Value("${app.community.rabbit-exchange}") String communityExchange,
            @Value("${app.community.rabbit-routing-key}") String communityRoutingKey,
            @Value("${app.knowledge.rabbit-exchange}") String knowledgeExchange,
            @Value("${app.knowledge.rabbit-routing-key}") String knowledgeRoutingKey,
            @Value("${app.search.rabbit-exchange}") String searchExchange,
            @Value("${app.search.rabbit-routing-key}") String searchRoutingKey,
            PlatformMetricsService metrics
    ) {
        this.mapper = mapper;
        this.rabbitPublisher = rabbitPublisher;
        this.communityExchange = communityExchange;
        this.communityRoutingKey = communityRoutingKey;
        this.knowledgeExchange = knowledgeExchange;
        this.knowledgeRoutingKey = knowledgeRoutingKey;
        this.searchExchange = searchExchange;
        this.searchRoutingKey = searchRoutingKey;
        this.metrics = metrics;
    }

    /** 定时认领 Outbox 并投递 RabbitMQ；失败采用指数退避，下次继续发布。 */
    @Scheduled(fixedDelayString = "${app.community.outbox-publish-delay-ms:3000}")
    @Transactional
    public void publishDue() {
        //查询当前时间到期、最多20条待发布的事件,每次只处理20条
        for (OutboxEventEntity event : mapper.findDue(Instant.now(), 20)) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEventEntity event) {
        MDC.put("eventId", event.id());
        try {
            Instant now = Instant.now();
            // 数据库原子认领为事件设置五分钟处理租约；其他实例或已完成事件直接跳过。
            if (mapper.claim(event.id(), now.plus(5, ChronoUnit.MINUTES)) == 0) return;
            try {
                boolean knowledgeEvent = "KNOWLEDGE_SUBMISSION".equals(event.aggregateType());
                boolean searchEvent = event.aggregateType().startsWith("SEARCH_");
                String exchange = searchEvent
                        ? searchExchange : (knowledgeEvent ? knowledgeExchange : communityExchange);
                String routingKey = searchEvent
                        ? searchRoutingKey : (knowledgeEvent ? knowledgeRoutingKey : communityRoutingKey);
                rabbitPublisher.send(
                        exchange, routingKey, event.payloadJson(), event.id(), event.eventType()
                );
                mapper.markPublished(event.id(), Instant.now());
                metrics.recordOutboxPublished(true);
            } catch (RuntimeException exception) {
                long delaySeconds = Math.min(300, 1L << Math.min(event.attempts() + 1, 8));
                mapper.markFailed(event.id(), Instant.now().plusSeconds(delaySeconds));
                metrics.recordOutboxPublished(false);
                log.warn("Outbox event {} publish failed: {}", event.id(), exception.toString());
            }
        } finally {
            MDC.remove("eventId");
        }
    }
}
