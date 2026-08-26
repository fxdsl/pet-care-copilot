package com.petassistant.business.data.dto.response;

import java.util.List;

/** 关注和粉丝列表的分页响应。 */
public record PublicUserPageResponse(
        List<PublicUserSummaryResponse> items,
        int page,
        int size,
        long total
) {
}
