package com.petassistant.business.service;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.response.CommunityPostResponse;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 帖子详情 Cache-Aside、浏览计数 Hash 和 Bloom Filter 防穿透。 */
@Service
public class CommunityPostCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityPostCacheService.class);
    private static final String DIRTY_COUNTER_SET = "community:post:counter:dirty";

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<RedissonClient> redissonProvider;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final String bloomName;
    private final String bloomReadyKey;

    public CommunityPostCacheService(
            StringRedisTemplate redisTemplate,
            ObjectProvider<RedissonClient> redissonProvider,
            ObjectMapper objectMapper,
            @Value("${app.community.post-cache-ttl:10m}") Duration ttl,
            @Value("${app.community.bloom-name:community:post:bloom}") String bloomName,
            @Value("${app.community.bloom-ready-key:community:post:bloom:ready}") String bloomReadyKey
    ) {
        this.redisTemplate = redisTemplate;
        this.redissonProvider = redissonProvider;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.bloomName = bloomName;
        this.bloomReadyKey = bloomReadyKey;
    }

    public CommunityPostResponse get(String postId) {
        try {
            String json = redisTemplate.opsForValue().get(detailKey(postId));
            return json == null ? null : objectMapper.readValue(json, CommunityPostResponse.class);
        } catch (Exception exception) {
            log.warn("Community post cache read failed: {}", exception.toString());
            return null;
        }
    }

    public void put(CommunityPostResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    detailKey(response.id()), objectMapper.writeValueAsString(response), ttl
            );
        } catch (Exception exception) {
            log.warn("Community post cache write failed: {}", exception.toString());
        }
    }

    public void evict(String postId) {
        try {
            redisTemplate.delete(detailKey(postId));
        } catch (DataAccessException exception) {
            log.warn("Community post cache eviction failed: {}", exception.toString());
        }
    }

    /** 返回当前尚未回写 MySQL 的浏览增量；Redis 故障时返回 0，不阻塞详情读取。 */
    public long incrementView(String postId) {
        try {
            Long value = redisTemplate.opsForHash().increment(counterKey(postId), "viewDelta", 1);
            redisTemplate.opsForSet().add(DIRTY_COUNTER_SET, postId);
            return value == null ? 0 : value;
        } catch (DataAccessException exception) {
            log.warn("Community view counter increment failed: {}", exception.toString());
            return 0;
        }
    }

    /** Bloom 未完成重建时必须放行到 MySQL，避免 Redis 丢失造成合法帖子假阴性。 */
    public boolean mightExist(String postId) {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(bloomReadyKey))) return true;
            return redissonProvider.getObject().<String>getBloomFilter(bloomName).contains(postId);
        } catch (RuntimeException exception) {
            log.warn("Community bloom check failed, fallback to MySQL: {}", exception.toString());
            return true;
        }
    }

    public void addPublishedId(String postId) {
        try {
            bloom().add(postId);
        } catch (RuntimeException exception) {
            log.warn("Community bloom add failed: {}", exception.toString());
        }
    }

    /** 启动时从 MySQL 全量重建已发布 ID，并最后设置 ready 标记。 */
    public void initializeBloom(List<String> publishedIds) {
        try {
            RBloomFilter<String> bloom = bloom();
            for (String id : publishedIds) bloom.add(id);
            redisTemplate.opsForValue().set(bloomReadyKey, "1");
        } catch (RuntimeException exception) {
            log.warn("Community bloom warmup skipped: {}", exception.toString());
        }
    }

    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }

    public static String dirtyCounterSet() { return DIRTY_COUNTER_SET; }
    public static String counterKey(String postId) { return "community:post:counter:" + postId; }

    private RBloomFilter<String> bloom() {
        RBloomFilter<String> filter = redissonProvider.getObject().getBloomFilter(bloomName);
        filter.tryInit(100_000L, 0.01);
        return filter;
    }

    private static String detailKey(String postId) {
        return "community:post:" + postId; }
}
