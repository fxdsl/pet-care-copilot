package com.petassistant.business.data.entity;

import java.time.Instant;

/** 社区评论数据库实体；只允许一级评论和一级回复。 */
public record CommunityCommentEntity(
        String id,
        String postId,
        String authorId,
        String parentId,
        String rootId,
        int depth,
        String content,
        String status,
        long likeCount,
        Instant createdAt,
        Instant updatedAt
) { }
