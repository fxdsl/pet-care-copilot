package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 评论联表后的内部投影，响应层不会暴露数据库密码等用户字段。 */
public record CommunityCommentView(
        String id,
        String postId,
        String authorId,
        String authorUsername,
        String authorDisplayName,
        String parentId,
        String rootId,
        int depth,
        String content,
        String status,
        long likeCount,
        Instant createdAt,
        Instant updatedAt
) { }
