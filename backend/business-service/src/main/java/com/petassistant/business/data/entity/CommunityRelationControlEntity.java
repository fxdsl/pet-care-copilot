package com.petassistant.business.data.entity;

import java.time.Instant;

/** 屏蔽或拉黑关系的 MySQL 最终事实。 */
public record CommunityRelationControlEntity(
        String id, String actorUserId, String targetUserId, String relationType,
        boolean active, Instant createdAt, Instant updatedAt
) { }
