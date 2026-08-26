package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 返回给当前登录用户的安全资料，不包含密码散列。 */
public record CurrentUserResponse(
        String id,
        String username,
        String displayName,
        String role,
        String status,
        String avatarUrl,
        String bio,
        String region,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
