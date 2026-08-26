package com.petassistant.business.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 最近 12 条模型上下文缓存，采用 Cache-Aside 并允许 Redis 故障降级。 */
@Service
public class ConversationContextCacheService {

    private static final Logger log = LoggerFactory.getLogger(ConversationContextCacheService.class);
    private static final String KEY_PREFIX = "pet-assistant:conversation:context:";
    private static final String KEY_VERSION = ":v1";
    private static final TypeReference<List<ConversationContextMessage>> VALUE_TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    /** 注入 Redis、JSON 工具和 30 分钟 TTL。 */
    public ConversationContextCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.conversation-context-ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /** 命中返回有序上下文；连接或 JSON 异常均按未命中处理。 */
    public Optional<List<ConversationContextMessage>> get(String conversationId) {
        try {
            String value = redisTemplate.opsForValue().get(key(conversationId));
            if (value == null) {
                return Optional.empty();
            }
            log.debug("Conversation context source=REDIS conversationId={}", conversationId);
            return Optional.of(objectMapper.readValue(value, VALUE_TYPE));
        } catch (Exception error) {
            log.debug("Redis context read failed; fallback=MYSQL conversationId={}", conversationId);
            return Optional.empty();
        }
    }

    /** 回源 MySQL 后写入最多 12 条上下文，Redis 故障不影响问答。 */
    public void put(String conversationId, List<ConversationContextMessage> messages) {
        List<ConversationContextMessage> bounded = tail(messages, 12);
        try {
            redisTemplate.opsForValue().set(
                    key(conversationId), objectMapper.writeValueAsString(bounded), ttl
            );
        } catch (Exception error) {
            log.debug("Redis context write failed conversationId={}", conversationId);
        }
    }

    /** 在事务提交后删除旧上下文；回滚时不会误删仍然有效的缓存。 */
    public void evictAfterCommit(String conversationId) {
        afterCommit(() -> evict(conversationId));
    }

    /** 在本轮双向消息提交后写入最新上下文，保证下一次追问可以命中。 */
    public void putAfterCommit(String conversationId, List<ConversationContextMessage> messages) {
        List<ConversationContextMessage> snapshot = List.copyOf(tail(messages, 12));
        afterCommit(() -> put(conversationId, snapshot));
    }

    /** 删除指定版本缓存键，异常只记录元数据。 */
    public void evict(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
        } catch (Exception error) {
            log.debug("Redis context eviction failed conversationId={}", conversationId);
        }
    }

    /** 事务外调用立即执行；事务内注册 afterCommit 回调。 */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** 保留时间顺序不变的最后 limit 条。 */
    private static List<ConversationContextMessage> tail(
            List<ConversationContextMessage> messages,
            int limit
    ) {
        int fromIndex = Math.max(0, messages.size() - limit);
        return messages.subList(fromIndex, messages.size());
    }

    /** 构造带业务命名空间和版本号的 Key。 */
    private static String key(String conversationId) {
        return KEY_PREFIX + conversationId + KEY_VERSION;
    }
}
