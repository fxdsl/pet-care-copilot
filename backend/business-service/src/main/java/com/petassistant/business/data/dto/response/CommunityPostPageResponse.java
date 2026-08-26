package com.petassistant.business.data.dto.response;

import java.util.List;

/** 社区帖子分页响应。 */
public record CommunityPostPageResponse(
        List<CommunityPostResponse> items,
        int page,
        int size,
        long total
) { }
