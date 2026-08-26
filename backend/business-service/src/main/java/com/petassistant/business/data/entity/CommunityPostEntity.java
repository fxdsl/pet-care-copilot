package com.petassistant.business.data.entity;

import java.time.Instant;

/** 社区帖子数据库实体，计数最终值保存在 MySQL。 */
public record CommunityPostEntity(
        String id,
        String authorId,
        String petProfileId,
        String topicId,
        String title,
        String content,
        String region,
        Double latitude,
        Double longitude,
        String status,
        long viewCount,
        long likeCount,
        long commentCount,
        long favoriteCount,
        int version,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) { }
