package com.petassistant.business.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.petassistant.business.config.SecurityProperties;
import com.petassistant.business.data.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * 访问令牌签发与校验服务。JWT 只保存身份摘要，宠物和用户资料仍从业务层读取。
 */
@Service
public class JwtTokenService {

    private final SecurityProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(SecurityProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
    }

    /** 签发带用户 ID、用户名和角色的短期 HS256 访问令牌。 */
    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .subject(user.id())
                .claim("username", user.username())
                .claim("role", user.role())
                .claim("securityVersion", user.securityVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    /** 验证签名、签发方和过期时间后恢复最小登录主体。 */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getJwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        Number securityVersion = claims.get("securityVersion", Number.class);
        if (claims.getSubject() == null || username == null || role == null || securityVersion == null) {
            throw new IllegalArgumentException("访问令牌缺少必要身份字段");
        }
        return new AuthenticatedUser(claims.getSubject(), username, role, securityVersion.longValue());
    }

    public long accessTokenExpiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
