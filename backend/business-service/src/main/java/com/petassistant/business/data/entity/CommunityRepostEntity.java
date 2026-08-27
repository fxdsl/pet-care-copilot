package com.petassistant.business.data.entity;

import java.time.Instant;

/** 转发事实；active=false 保留幂等撤销记录并允许同一关系再次启用。 */
public record CommunityRepostEntity(
        String id, String postId, String userId, String quoteContent,
        boolean active, Instant createdAt, Instant updatedAt
) { }
