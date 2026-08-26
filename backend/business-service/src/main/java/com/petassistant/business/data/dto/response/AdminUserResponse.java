package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 管理端用户摘要，不返回密码散列等敏感字段。 */
public record AdminUserResponse(
        String id,
        String username,
        String displayName,
        String role,
        String status,
        long securityVersion,
        String region,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
