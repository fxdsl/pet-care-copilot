package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.petassistant.business.data.entity.ScheduledJobExecutionEntity;
import com.petassistant.business.data.mapper.SchedulerMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Quartz 维护任务的统一执行器：分布式锁、三次尝试和 MySQL 审计集中在这里。 */
@Service
public class ScheduledMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledMaintenanceService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final SchedulerMapper mapper;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PlatformMetricsService metrics;
    private final String communityQueue;
    private final String knowledgeQueue;
    private final String searchQueue;

    public ScheduledMaintenanceService(
            SchedulerMapper mapper,
            RedissonClient redissonClient,
            RabbitTemplate rabbitTemplate,
            StringRedisTemplate redisTemplate,
            PlatformTransactionManager transactionManager,
            PlatformMetricsService metrics,
            @Value("${app.community.rabbit-queue}") String communityQueue,
            @Value("${app.knowledge.rabbit-queue}") String knowledgeQueue,
            @Value("${app.search.rabbit-queue}") String searchQueue
    ) {
        this.mapper = mapper;
        this.redissonClient = redissonClient;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.communityQueue = communityQueue;
        this.knowledgeQueue = knowledgeQueue;
        this.searchQueue = searchQueue;
    }

    /** 每个实例都有 Quartz 触发器，但同一 task 只有拿到 Redisson 锁的实例会真正执行。 */
    public void execute(String task) {
        RLock lock = redissonClient.getLock("scheduler:lock:" + task.toLowerCase());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!acquired) {
                log.debug("Quartz task {} skipped because another instance owns the lock", task);
                return;
            }
            executeWithAudit(task);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("Quartz task {} interrupted while acquiring lock", task);
        } catch (RuntimeException error) {
            log.error("Quartz task {} could not start: {}", task, error.toString());
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void executeWithAudit(String task) {
        Instant startedAt = Instant.now();
        String executionId = UUID.randomUUID().toString();
        String batchKey = task + ":" + startedAt.truncatedTo(ChronoUnit.SECONDS);
        mapper.insertExecution(new ScheduledJobExecutionEntity(
                executionId, task, batchKey, "RUNNING", 1, 0, null, startedAt, null, null
        ));

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Integer result = transactionTemplate.execute(status -> perform(task));
                int processed = result == null ? 0 : result;
                mapper.finishExecution(executionId, "COMPLETED", attempt, processed, null, Instant.now());
                return;
            } catch (RuntimeException error) {
                lastError = error;
                log.warn("Quartz task {} attempt {} failed: {}", task, attempt, error.toString());
            }
        }
        String message = abbreviate(lastError == null ? "未知错误" : lastError.toString(), 1000);
        mapper.finishExecution(executionId, "MANUAL_REVIEW", MAX_ATTEMPTS, 0, message, Instant.now());
    }

    private int perform(String task) {
        Instant now = Instant.now();
        return switch (task) {
            case "TREND_SNAPSHOT" -> mapper.insertTrendSnapshot(now, 50);
            case "KNOWLEDGE_EXPIRY" -> mapper.expireKnowledge(now);
            case "OUTBOX_RECOVERY" -> mapper.retryStuckSearchEvents(now) + mapper.enqueueOutboxManualReviews(now);
            case "AUDIT_ARCHIVE" -> archiveAudits(now);
            case "QUEUE_BACKLOG" -> snapshotQueueBacklog();
            default -> throw new IllegalArgumentException("未知 Quartz 任务：" + task);
        };
    }

    /** 先复制后删除，两个 SQL 只有在全部成功时才由调用事务提交。 */
    private int archiveAudits(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(180));
        int archived = mapper.archiveAdminAudits(cutoff, now);
        mapper.deleteArchivedAdminAudits(cutoff);
        return archived;
    }

    /** RabbitMQ 被动声明只读取消息数；结果写 Redis Hash，监控页丢缓存后可重新采样。 */
    private int snapshotQueueBacklog() {
        Map<String, Integer> counts = Map.of(
                communityQueue, messageCount(communityQueue),
                knowledgeQueue, messageCount(knowledgeQueue),
                searchQueue, messageCount(searchQueue)
        );
        counts.forEach((queue, count) -> redisTemplate.opsForHash()
                .put("platform:queue:backlog", queue, Integer.toString(count)));
        counts.forEach(metrics::recordQueueBacklog);
        redisTemplate.expire("platform:queue:backlog", Duration.ofMinutes(10));
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int messageCount(String queue) {
        Integer count = rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount());
        return count == null ? 0 : count;
    }

    private static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
