package com.petassistant.business.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.petassistant.business.config.SecurityProperties;
import com.petassistant.business.exception.AuthSessionUnavailableException;
import com.petassistant.business.exception.AuthenticationFailedException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 刷新令牌服务。浏览器拿到随机原文，Redis 只保存 SHA-256 摘要和用户 ID。
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<RedissonClient> redissonProvider;
    private final Duration refreshTtl;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            ObjectProvider<RedissonClient> redissonProvider,
            SecurityProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.redissonProvider = redissonProvider;
        this.refreshTtl = properties.getRefreshTokenTtl();
    }

    /** 创建格式为 tokenId.secret 的刷新令牌。 */
    public String issue(String userId) {
        String tokenId = UUID.randomUUID().toString();
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        try {
            redisTemplate.opsForValue().set(
                    key(tokenId),
                    userId + ":" + sha256(secret),
                    refreshTtl
            );
            // 反向索引用于管理员禁用账号或调整角色时撤销该用户的全部刷新会话。
            redisTemplate.opsForSet().add(userKey(userId), tokenId);
            redisTemplate.expire(userKey(userId), refreshTtl);
            return tokenId + "." + secret;
        } catch (DataAccessException exception) {
            try {
                redisTemplate.delete(key(tokenId));
            } catch (RuntimeException ignored) {
                // 原始 Redis 异常优先返回，清理失败的孤儿 Key 最多存活 refreshTtl。
            }
            throw new AuthSessionUnavailableException(exception);
        }
    }

    /**
     * 在 Redisson 分布式锁内校验并删除旧令牌，再签发新令牌，防止并发刷新重复成功。
     */
    public RotatedToken rotate(String presentedToken) {
        TokenParts parts = parse(presentedToken);
        RLock lock;
        try {
            lock = redissonProvider.getObject().getLock("auth:refresh:lock:" + parts.tokenId());
        } catch (RuntimeException exception) {
            throw new AuthSessionUnavailableException(exception);
        }
        boolean acquired = false;
        try {
            acquired = lock.tryLock(1, 5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new AuthenticationFailedException();
            }
            String stored = redisTemplate.opsForValue().get(key(parts.tokenId()));
            String userId = validateStored(stored, parts.secret());
            redisTemplate.delete(key(parts.tokenId()));
            redisTemplate.opsForSet().remove(userKey(userId), parts.tokenId());
            return new RotatedToken(userId, issue(userId));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthSessionUnavailableException(exception);
        } catch (DataAccessException exception) {
            throw new AuthSessionUnavailableException(exception);
        } finally {
            try {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (RuntimeException exception) {
                // 令牌已经完成旋转时，解锁网络异常只记录；5 秒租约会自动释放，不能让浏览器丢失新令牌。
                log.warn("Refresh token lock release failed: {}", exception.toString());
            }
        }
    }

    /** 退出时只在令牌摘要匹配的情况下撤销当前会话。 */
    public void revoke(String presentedToken) {
        TokenParts parts = parse(presentedToken);
        try {
            String stored = redisTemplate.opsForValue().get(key(parts.tokenId()));
            String userId = validateStored(stored, parts.secret());
            redisTemplate.delete(key(parts.tokenId()));
            redisTemplate.opsForSet().remove(userKey(userId), parts.tokenId());
        } catch (DataAccessException exception) {
            throw new AuthSessionUnavailableException(exception);
        }
    }

    /** 管理员修改角色或状态时按反向索引撤销目标用户的全部刷新令牌。 */
    public void revokeAll(String userId) {
        try {
            Set<String> tokenIds = redisTemplate.opsForSet().members(userKey(userId));
            if (tokenIds != null && !tokenIds.isEmpty()) {
                Set<String> tokenKeys = new HashSet<>();
                for (String tokenId : tokenIds) tokenKeys.add(key(tokenId));
                redisTemplate.delete(tokenKeys);
            }
            redisTemplate.delete(userKey(userId));
        } catch (DataAccessException exception) {
            throw new AuthSessionUnavailableException(exception);
        }
    }

    private static String validateStored(String stored, String secret) {
        if (stored == null) {
            throw new AuthenticationFailedException();
        }
        int separator = stored.indexOf(':');
        if (separator <= 0) {
            throw new AuthenticationFailedException();
        }
        String expectedHash = stored.substring(separator + 1);
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                sha256(secret).getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new AuthenticationFailedException();
        }
        return stored.substring(0, separator);
    }

    private static TokenParts parse(String token) {
        int separator = token == null ? -1 : token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1 || token.indexOf('.', separator + 1) >= 0) {
            throw new AuthenticationFailedException();
        }
        return new TokenParts(token.substring(0, separator), token.substring(separator + 1));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static String key(String tokenId) {
        return "auth:refresh:" + tokenId;
    }

    private static String userKey(String userId) {
        return "auth:refresh:user:" + userId;
    }

    public record RotatedToken(String userId, String refreshToken) {
    }

    private record TokenParts(String tokenId, String secret) {
    }
}
