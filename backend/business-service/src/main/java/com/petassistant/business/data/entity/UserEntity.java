package com.petassistant.business.data.entity;

import java.time.Instant;

/** 用户数据库实体，与 {@code app_user} 表一一对应。 */
public record UserEntity(
        String id,
        String username,
        String passwordHash,
        String displayName,
        String role,
        String status,
        long securityVersion,
        String avatarUrl,
        String bio,
        String region,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
