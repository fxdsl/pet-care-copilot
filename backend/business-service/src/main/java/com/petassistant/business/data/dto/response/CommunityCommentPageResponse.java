package com.petassistant.business.data.dto.response;

import java.util.List;

/** 评论分页响应。 */
public record CommunityCommentPageResponse(
        List<CommunityCommentResponse> items,
        int page,
        int size,
        long total
) { }
