package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.petassistant.business.data.dto.response.CurrentUserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 当前用户资料的 Cache-Aside 缓存；Redis 不可用时安全回源 MySQL。 */
@Service
public class UserSessionCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public UserSessionCacheService(
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.user-session-ttl:15m}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /** 读取不含密码的用户摘要；字段不完整时按缓存未命中处理。 */
    public Optional<CurrentUserResponse> get(String userId) {
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(key(userId));
            if (values.isEmpty() || values.get("username") == null) {
                return Optional.empty();
            }
            return Optional.of(new CurrentUserResponse(
                    userId,
                    string(values, "username"),
                    nullable(values, "displayName"),
                    string(values, "role"),
                    string(values, "status"),
                    nullable(values, "avatarUrl"),
                    nullable(values, "bio"),
                    nullable(values, "region"),
                    instant(values, "lastLoginAt"),
                    instant(values, "createdAt"),
                    instant(values, "updatedAt")
            ));
        } catch (DataAccessException | IllegalArgumentException exception) {
            log.warn("Redis user session cache read failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    /** 写入 Hash 并设置 TTL；失败不影响已通过 JWT 的 MySQL 权限校验。 */
    public void put(CurrentUserResponse user) {
        Map<String, String> values = new HashMap<>();
        put(values, "username", user.username());
        put(values, "displayName", user.displayName());
        put(values, "role", user.role());
        put(values, "status", user.status());
        put(values, "avatarUrl", user.avatarUrl());
        put(values, "bio", user.bio());
        put(values, "region", user.region());
        put(values, "lastLoginAt", user.lastLoginAt());
        put(values, "createdAt", user.createdAt());
        put(values, "updatedAt", user.updatedAt());
        try {
            redisTemplate.opsForHash().putAll(key(user.id()), values);
            redisTemplate.expire(key(user.id()), ttl);
        } catch (DataAccessException exception) {
            log.warn("Redis user session cache write failed: {}", exception.toString());
        }
    }

    public void evict(String userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException exception) {
            log.warn("Redis user session cache eviction failed: {}", exception.toString());
        }
    }

    private static void put(Map<String, String> values, String key, Object value) {
        values.put(key, value == null ? "" : value.toString());
    }

    private static String string(Map<Object, Object> values, String key) {
        String value = nullable(values, key);
        if (value == null) {
            throw new IllegalArgumentException("缓存缺少字段 " + key);
        }
        return value;
    }

    private static String nullable(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Instant instant(Map<Object, Object> values, String key) {
        String value = nullable(values, key);
        return value == null ? null : Instant.parse(value);
    }

    private static String key(String userId) {
        return "user:session:" + userId;
    }
}
