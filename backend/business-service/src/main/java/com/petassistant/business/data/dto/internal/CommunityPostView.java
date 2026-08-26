package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 帖子与作者、话题联表后的内部只读投影。 */
public record CommunityPostView(
        String id,
        String authorId,
        String authorUsername,
        String authorDisplayName,
        String petProfileId,
        String petName,
        String topicId,
        String topicName,
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
