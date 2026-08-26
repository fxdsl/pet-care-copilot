package com.petassistant.business.service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.response.SearchTrendingResponse;
import com.petassistant.business.data.dto.response.UnifiedSearchResponse;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 搜索专用 Redis 访问层：String 保存结果和版本，ZSet 保存趋势，BloomFilter
 * 加速公开文档存在性判断，Lua 原子限制联想接口频率。
 */
@Service
public class SearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(SearchCacheService.class);
    private static final String VERSION_KEY = "search:index:version";
    private static final String TRENDING_KEY = "search:trending";
    private static final String BLOOM_NAME = "search:public:id:bloom";
    private static final String BLOOM_READY_KEY = "search:public:id:bloom:ready";
    private static final Duration SUGGESTION_CACHE_TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> RATE_LIMIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final SearchProperties properties;

    public SearchCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RedissonClient redissonClient,
            SearchProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    /** 缓存只保存公开查询响应；Redis 故障时直接返回空，让调用方继续查事实源。 */
    public UnifiedSearchResponse getResult(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            return json == null ? null : objectMapper.readValue(json, UnifiedSearchResponse.class);
        } catch (Exception error) {
            log.debug("Search result cache read failed: {}", error.toString());
            return null;
        }
    }

    public void putResult(String cacheKey, UnifiedSearchResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(response), properties.resultCacheTtl()
            );
        } catch (Exception error) {
            log.debug("Search result cache write failed: {}", error.toString());
        }
    }

    /** 索引版本进入缓存 Key；重建后递增即可让旧结果自然过期。 */
    public long indexVersion() {
        try {
            String value = redisTemplate.opsForValue().get(VERSION_KEY);
            if (value == null) {
                redisTemplate.opsForValue().setIfAbsent(VERSION_KEY, "1");
                return 1L;
            }
            return Long.parseLong(value);
        } catch (Exception error) {
            return 1L;
        }
    }

    public long incrementIndexVersion() {
        try {
            Long version = redisTemplate.opsForValue().increment(VERSION_KEY);
            return version == null ? indexVersion() : version;
        } catch (Exception error) {
            return indexVersion();
        }
    }

    /** 只把不含明显账号信息的短查询计入公开趋势。 */
    public void recordTrending(String normalizedQuery) {
        if (!isSafeTrendingQuery(normalizedQuery)) return;
        try {
            redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, normalizedQuery, 1D);
            redisTemplate.expire(TRENDING_KEY, Duration.ofDays(7));
            Long size = redisTemplate.opsForZSet().size(TRENDING_KEY);
            if (size != null && size > 200) redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, size - 201);
        } catch (RuntimeException error) {
            log.debug("Search trending update failed: {}", error.toString());
        }
    }

    public List<SearchTrendingResponse> trending(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> values = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(TRENDING_KEY, 0, limit - 1L);
            if (values == null) return List.of();
            List<SearchTrendingResponse> result = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> value : values) {
                if (value.getValue() != null) {
                    result.add(new SearchTrendingResponse(value.getValue(), value.getScore() == null ? 0 : value.getScore()));
                }
            }
            return result;
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    /** 返回 false 表示当前一分钟已超过联想请求上限。 */
    public boolean allowSuggestion(String userId, int limit) {
        try {
            Long count = redisTemplate.execute(
                    RATE_LIMIT, List.of("search:suggest:rate:" + userId), String.valueOf(Duration.ofMinutes(1).toMillis())
            );
            return count == null || count <= limit;
        } catch (RuntimeException error) {
            // Redis 不是搜索权限事实源，故障时允许继续从 MySQL 获取公开联想。
            return true;
        }
    }

    /** Hash 缓存只保存公开标题联想；个人历史始终实时按 userId 从 MySQL 查询。 */
    public List<String> getPublicSuggestions(String normalizedQuery) {
        try {
            Object value = redisTemplate.opsForHash().get(suggestionKey(normalizedQuery), "items");
            return value == null ? null : objectMapper.readValue(value.toString(), new TypeReference<>() { });
        } catch (Exception error) {
            return null;
        }
    }

    public void putPublicSuggestions(String normalizedQuery, List<String> suggestions) {
        String key = suggestionKey(normalizedQuery);
        try {
            redisTemplate.opsForHash().putAll(key, java.util.Map.of(
                    "items", objectMapper.writeValueAsString(suggestions),
                    "generatedAt", String.valueOf(System.currentTimeMillis())
            ));
            redisTemplate.expire(key, SUGGESTION_CACHE_TTL);
        } catch (Exception error) {
            log.debug("Public suggestion cache write failed: {}", error.toString());
        }
    }

    /** 全量重建和增量写入都维护同一个布隆过滤器。 */
    public boolean addPublicDocument(String type, String id) {
        try {
            RBloomFilter<String> bloom = redissonClient.getBloomFilter(BLOOM_NAME);
            bloom.tryInit(100_000L, 0.01D);
            bloom.add(type + ":" + id);
            return true;
        } catch (RuntimeException error) {
            // 任何维护失败都撤销“已完成”标记，后续判断自动改为放行，防止 Bloom 假阴性。
            try { redisTemplate.delete(BLOOM_READY_KEY); } catch (RuntimeException ignored) { }
            log.debug("Search bloom update failed: {}", error.toString());
            return false;
        }
    }

    /** Bloom 未完成或 Redis 故障时必须放行，避免把真实索引文档误判为不存在。 */
    public boolean mightContainPublicDocument(String type, String id) {
        try {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(BLOOM_READY_KEY))) return true;
            return redissonClient.<String>getBloomFilter(BLOOM_NAME).contains(type + ":" + id);
        } catch (RuntimeException error) {
            return true;
        }
    }

    public void markBloomReady() {
        try {
            redisTemplate.opsForValue().set(BLOOM_READY_KEY, "1");
        } catch (RuntimeException error) {
            log.debug("Search bloom ready marker write failed: {}", error.toString());
        }
    }

    public void resetBloom() {
        try {
            redisTemplate.delete(BLOOM_READY_KEY);
            redissonClient.getBloomFilter(BLOOM_NAME).delete();
        } catch (RuntimeException error) {
            log.debug("Search bloom reset failed: {}", error.toString());
        }
    }

    private boolean isSafeTrendingQuery(String query) {
        if (query == null || query.length() < 2 || query.length() > 30) return false;
        return !query.matches(".*\\b1[3-9]\\d{9}\\b.*")
                && !query.matches("(?i).*\\b[\\w.%+-]+@[\\w.-]+\\.[A-Z]{2,}\\b.*");
    }

    private String suggestionKey(String query) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(query.getBytes(StandardCharsets.UTF_8));
            return "search:suggestion:" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM 缺少 SHA-256", error);
        }
    }
}
