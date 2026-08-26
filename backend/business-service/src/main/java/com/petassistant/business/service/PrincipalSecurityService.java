package com.petassistant.business.service;

import java.time.Duration;
import java.util.Map;

import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 校验 JWT 中的权限版本；Redis 故障时回源 MySQL，不能沿用无法确认的旧权限。 */
@Service
public class PrincipalSecurityService {

    private static final Logger log = LoggerFactory.getLogger(PrincipalSecurityService.class);

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public PrincipalSecurityService(
            UserMapper userMapper,
            StringRedisTemplate redisTemplate,
            @Value("${app.cache.principal-security-ttl:15m}") Duration ttl
    ) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /** 只有 ACTIVE、角色相同且安全版本相同的主体才能继续访问。 */
    public boolean isCurrent(AuthenticatedUser principal) {
        // 权限撤销要求立即生效，因此 MySQL 始终参与授权判断；Redis 快照只用于观测和后续优化。
        UserEntity user = userMapper.findById(principal.userId());
        if (user == null) return false;
        SecuritySnapshot snapshot = new SecuritySnapshot(user.role(), user.status(), user.securityVersion());
        writeCache(principal.userId(), snapshot);
        return "ACTIVE".equals(snapshot.status())
                && principal.role().equals(snapshot.role())
                && principal.securityVersion() == snapshot.securityVersion();
    }

    /** 权限变化提交后删除旧快照；下一次请求会回源读取新版本。 */
    public void evict(String userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException exception) {
            log.warn("Principal security cache eviction failed: {}", exception.toString());
        }
    }

    private void writeCache(String userId, SecuritySnapshot snapshot) {
        try {
            redisTemplate.opsForHash().putAll(key(userId), Map.of(
                    "role", snapshot.role(), "status", snapshot.status(),
                    "securityVersion", Long.toString(snapshot.securityVersion())
            ));
            redisTemplate.expire(key(userId), ttl);
        } catch (DataAccessException exception) {
            log.warn("Principal security cache write failed: {}", exception.toString());
        }
    }

    private static String key(String userId) {
        return "auth:principal:" + userId;
    }

    private record SecuritySnapshot(String role, String status, long securityVersion) {
    }
}
