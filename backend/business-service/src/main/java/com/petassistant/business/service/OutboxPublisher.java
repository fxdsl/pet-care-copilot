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

    //赋值构造函数
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
        //将当前事件 ID 放入线程本地变量，这样后续所有日志都会自动携带 eventId 字段
        //作用：在分布式系统中快速定位某条事件的完整处理链路
        MDC.put("eventId", event.id());
        try {
            Instant now = Instant.now();
            // 数据库原子认领为事件设置五分钟处理租约；其他实例或已完成事件直接跳过。
            //UPDATE integration_outbox
            //SET status = 'PROCESSING',           -- 状态改为"处理中"
            //    attempts = attempts + 1,         -- 尝试次数+1
            //    next_attempt_at = '2026-...+05'  -- 设置新的重试时间（5分钟后）
            //WHERE id = 'evt_abc123'              -- 匹配当前事件
            //  AND status IN ('PENDING', 'FAILED', 'PROCESSING')  -- 只能是这三种状态
            if (mapper.claim(event.id(), now.plus(5, ChronoUnit.MINUTES)) == 0) return;
            try {
                //事件类型智能路由
                //针对你的场景：KnowledgeSubmissionService.java:227 写入的 "KNOWLEDGE_SUBMISSION" 类型事件会走 knowledge 路由。
                //判断是否知识提交事件
                boolean knowledgeEvent = "KNOWLEDGE_SUBMISSION".equals(event.aggregateType());
                //判断是否搜索相关事件
                //startsWith("SEARCH_"),检查事件类型是否以 "SEARCH_" 开头
                boolean searchEvent = event.aggregateType().startsWith("SEARCH_");
                //三元表达式选择 Exchange,消息路由中心，接收消息并根据规则分发到队列
                //不同业务领域使用不同的 Exchange，实现 物理隔离
                //比如：KNOWLEDGE_SUBMISSION 事件，先进入(knowledgeEvent ? knowledgeExchange : communityExchange)。
                //在进行下一步判断选择knowledgeExchange
                String exchange = searchEvent
                        ? searchExchange : (knowledgeEvent ? knowledgeExchange : communityExchange);
                //三元表达式选择 Routing Key,消息路由规则，根据消息内容将消息分发到指定队列
                String routingKey = searchEvent
                        ? searchRoutingKey : (knowledgeEvent ? knowledgeRoutingKey : communityRoutingKey);
                //发送到对应队列。
                rabbitPublisher.send(
                        exchange, routingKey, event.payloadJson(), event.id(), event.eventType()
                );
                //调用 Mapper 更新数据库状态为已发布
                mapper.markPublished(event.id(), Instant.now());
                //调用监控系统服务，记录 outbox 发布成功 的计数器
                //参数 true 表示成功
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
