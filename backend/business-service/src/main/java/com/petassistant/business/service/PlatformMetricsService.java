package com.petassistant.business.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/** 项目级业务指标；不写入问题正文、令牌、手机号等高基数字段。 */
@Service
public class PlatformMetricsService {

    private final MeterRegistry registry;
    private final Counter agentToolCalls;
    private final Counter estimatedTokens;
    private final Counter outboxPublished;
    private final Counter outboxFailed;
    private final Timer knowledgeReviewLatency;
    private final ConcurrentHashMap<String, AtomicLong> queueBacklogs = new ConcurrentHashMap<>();

    public PlatformMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.agentToolCalls = registry.counter("pet_agent_tool_calls_total");
        this.estimatedTokens = registry.counter("pet_ai_estimated_tokens_total");
        this.outboxPublished = registry.counter("pet_outbox_publish_total", "result", "success");
        this.outboxFailed = registry.counter("pet_outbox_publish_total", "result", "failed");
        this.knowledgeReviewLatency = registry.timer("pet_knowledge_review_latency");
    }

    public void recordAgentUsage(String question, String answer, int toolCallCount) {
        agentToolCalls.increment(Math.max(0, toolCallCount));
        // FastAPI 暂未返回精确 usage，因此名称明确标注 estimated，避免把估算冒充账单数据。
        estimatedTokens.increment(Math.max(1, (safeLength(question) + safeLength(answer) + 1) / 2.0));
    }

    public void recordKnowledgeReviewLatency(Duration duration) {
        if (duration != null && !duration.isNegative()) knowledgeReviewLatency.record(duration);
    }

    public void recordOutboxPublished(boolean success) {
        (success ? outboxPublished : outboxFailed).increment();
    }

    public void recordQueueBacklog(String queue, long messages) {
        AtomicLong holder = queueBacklogs.computeIfAbsent(queue, name -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("pet_queue_backlog_messages", value, AtomicLong::get)
                    .description("RabbitMQ queue backlog sampled by Quartz")
                    .tag("queue", name)
                    .register(registry);
            return value;
        });
        holder.set(Math.max(0, messages));
    }

    private static int safeLength(String value) { return value == null ? 0 : value.length(); }
}
