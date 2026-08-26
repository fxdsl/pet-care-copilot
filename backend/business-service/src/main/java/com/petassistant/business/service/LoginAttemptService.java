package com.petassistant.business.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.petassistant.business.config.SecurityProperties;
import com.petassistant.business.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 使用 Lua 原子自增与过期时间保护登录和注册入口。 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties properties;

    public LoginAttemptService(StringRedisTemplate redisTemplate, SecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /** 每个 IP 在窗口内只允许有限次认证入口调用。Redis 故障时保留密码认证，不绕过身份校验。 */
    public void checkEndpointRate(String action, String ipAddress) {
        //判断IP地址是否为空或无效
        String safeIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
        // 构建Redis键名
        String key = "auth:rate:" + action + ":" + safeIp;
        // 原子性计数并设置过期时间
        Long count = increment(key, properties.getLoginRateWindow());
        // 检查是否超过限制
        if (count != null && count > properties.getLoginRateLimit()) {
            throw new TooManyRequestsException("操作过于频繁，请稍后再试");
        }
    }

    /** 失败次数达到阈值后暂时阻止该用户名继续尝试。 */
    public void requireNotLocked(String username) {
        try {
            String value = redisTemplate.opsForValue().get(failureKey(username));
            if (value != null && Integer.parseInt(value) >= properties.getLoginFailureLimit()) {
                throw new TooManyRequestsException("登录失败次数过多，请稍后再试");
            }
        } catch (DataAccessException exception) {
            log.warn("Redis login failure check unavailable: {}", exception.toString());
        }
    }

    public void recordFailure(String username) {
        increment(failureKey(username), properties.getLoginFailureWindow());
    }

    public void clearFailures(String username) {
        try {
            redisTemplate.delete(failureKey(username));
        } catch (DataAccessException exception) {
            log.warn("Redis login failure cleanup unavailable: {}", exception.toString());
        }
    }

    private Long increment(String key, Duration ttl) {
        try {
            return redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toMillis()));
        } catch (DataAccessException exception) {
            log.warn("Redis rate limiter unavailable for {}: {}", key, exception.toString());
            return null;
        }
    }

    private static String failureKey(String username) {
        return "auth:login:fail:" + username.trim().toLowerCase(Locale.ROOT);
    }
}
