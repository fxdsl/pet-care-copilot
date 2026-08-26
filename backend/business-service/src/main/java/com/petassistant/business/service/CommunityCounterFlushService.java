package com.petassistant.business.service;

import java.util.Set;
import java.util.List;

import com.petassistant.business.data.mapper.CommunityPostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 定时把 Redis 浏览增量回写 MySQL；失败会恢复增量并留在 dirty Set 中重试。 */
@Service
public class CommunityCounterFlushService {

    private static final Logger log = LoggerFactory.getLogger(CommunityCounterFlushService.class);
    private static final DefaultRedisScript<Long> TAKE_DELTA = new DefaultRedisScript<>(
            "local value = redis.call('HGET', KEYS[1], ARGV[1]); "
                    + "if not value then return 0 end; "
                    + "redis.call('HDEL', KEYS[1], ARGV[1]); return tonumber(value)",
            Long.class
    );

    private final CommunityPostMapper mapper;
    private final CommunityPostCacheService cache;
    private final StringRedisTemplate redisTemplate;

    public CommunityCounterFlushService(CommunityPostMapper mapper, CommunityPostCacheService cache) {
        this.mapper = mapper;
        this.cache = cache;
        this.redisTemplate = cache.redisTemplate();
    }

    @Scheduled(fixedDelay = 10_000)
    public void flush() {
        try {
            Set<String> dirtyIds = redisTemplate.opsForSet().members(CommunityPostCacheService.dirtyCounterSet());
            if (dirtyIds == null) return;
            for (String postId : dirtyIds.stream().limit(100).toList()) flushOne(postId);
        } catch (DataAccessException exception) {
            log.warn("Community counter flush skipped: {}", exception.toString());
        }
    }

    private void flushOne(String postId) {
        String key = CommunityPostCacheService.counterKey(postId);
        long delta = 0;
        try {
            Long taken = redisTemplate.execute(TAKE_DELTA, List.of(key), "viewDelta");
            delta = taken == null ? 0 : taken;
            if (delta <= 0) {
                redisTemplate.opsForSet().remove(CommunityPostCacheService.dirtyCounterSet(), postId);
                return;
            }
            if (mapper.addViews(postId, delta) == 0) {
                redisTemplate.delete(key);
            }
            redisTemplate.opsForSet().remove(CommunityPostCacheService.dirtyCounterSet(), postId);
            cache.evict(postId);
        } catch (RuntimeException exception) {
            log.warn("Community counter {} flush failed: {}", postId, exception.toString());
            // 已取出的增量必须补回 Redis，避免数据库瞬时失败造成浏览数据丢失。
            if (delta > 0) {
                try {
                    redisTemplate.opsForHash().increment(key, "viewDelta", delta);
                    redisTemplate.opsForSet().add(CommunityPostCacheService.dirtyCounterSet(), postId);
                } catch (RuntimeException restoreError) {
                    log.error("Community counter {} restore failed", postId, restoreError);
                }
            }
        }
    }
}
