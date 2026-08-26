package com.petassistant.business.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.petassistant.business.data.entity.OutboxEventEntity;
import com.petassistant.business.data.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定时认领 Outbox 并投递 RabbitMQ；失败采用指数退避，下次继续发布。 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final String communityExchange;
    private final String communityRoutingKey;
    private final String knowledgeExchange;
    private final String knowledgeRoutingKey;
    private final String searchExchange;
    private final String searchRoutingKey;

    public OutboxPublisher(
            OutboxEventMapper mapper,
            RabbitTemplate rabbitTemplate,
            @Value("${app.community.rabbit-exchange}") String communityExchange,
            @Value("${app.community.rabbit-routing-key}") String communityRoutingKey,
            @Value("${app.knowledge.rabbit-exchange}") String knowledgeExchange,
            @Value("${app.knowledge.rabbit-routing-key}") String knowledgeRoutingKey,
            @Value("${app.search.rabbit-exchange}") String searchExchange,
            @Value("${app.search.rabbit-routing-key}") String searchRoutingKey
    ) {
        this.mapper = mapper;
        this.rabbitTemplate = rabbitTemplate;
        this.communityExchange = communityExchange;
        this.communityRoutingKey = communityRoutingKey;
        this.knowledgeExchange = knowledgeExchange;
        this.knowledgeRoutingKey = knowledgeRoutingKey;
        this.searchExchange = searchExchange;
        this.searchRoutingKey = searchRoutingKey;
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
        Instant now = Instant.now();
        //使用数据库行锁"认领"该事件，设置5分钟超时
        //如果事件已被其他线程认领，直接返回
        //如果事件已被成功发布，直接返回
        //如果事件已被失败，直接返回
        //如果事件已被成功发布，直接返回
        //如果事件已被失败，直接返回
        if (mapper.claim(event.id(),
            now.plus(5,
                ChronoUnit.MINUTES)) == 0) return;
        try {
            boolean knowledgeEvent = "KNOWLEDGE_SUBMISSION".equals(event.aggregateType());
            boolean searchEvent = event.aggregateType().startsWith("SEARCH_");
            String exchange = searchEvent ? searchExchange : (knowledgeEvent ? knowledgeExchange : communityExchange);
            String routingKey = searchEvent ? searchRoutingKey : (knowledgeEvent ? knowledgeRoutingKey : communityRoutingKey);
            rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                event.payloadJson(),// 消息体（JSON字符串）
                message -> {
                    // 1. 设置内容类型为 JSON
                message.getMessageProperties().setContentType("application/json");
                    // 2. 设置消息ID（用于幂等性处理）
                message.getMessageProperties().setMessageId(event.id());
                    // 3. 设置自定义头：事件类型（方便消费者路由）
                message.getMessageProperties().setHeader("eventType", event.eventType());
                return message;
            });
            mapper.markPublished(event.id(), Instant.now());
        } catch (RuntimeException exception) {
            long delaySeconds = Math.min(300, 1L << Math.min(event.attempts() + 1, 8));
            mapper.markFailed(event.id(), Instant.now().plusSeconds(delaySeconds));
            log.warn("Outbox event {} publish failed: {}", event.id(), exception.toString());
        }
    }
}
