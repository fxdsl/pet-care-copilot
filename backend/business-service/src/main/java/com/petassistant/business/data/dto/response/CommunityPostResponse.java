package com.petassistant.business.data.dto.response;

import java.time.Instant;
import java.util.List;

/** 社区帖子详情/列表统一响应，不暴露 MinIO 对象 Key。 */
public record CommunityPostResponse(
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
        Instant updatedAt,
        List<CommunityMediaResponse> media,
        boolean viewerLiked,
        boolean viewerFavorited,
        boolean viewerFollowsAuthor
) { }
