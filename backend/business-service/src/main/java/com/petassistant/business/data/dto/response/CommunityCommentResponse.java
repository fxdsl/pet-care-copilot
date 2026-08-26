package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 社区评论响应，viewerCanDelete 由当前登录用户动态计算。 */
public record CommunityCommentResponse(
        String id,
        String postId,
        String authorId,
        String authorUsername,
        String authorDisplayName,
        String parentId,
        String rootId,
        int depth,
        String content,
        long likeCount,
        boolean viewerCanDelete,
        Instant createdAt,
        Instant updatedAt
) { }
