package com.petassistant.business.data.dto.response;

import java.util.List;

/** 单个私信会话的分页消息响应。 */
public record DirectMessagePageResponse(
        List<DirectMessageResponse> items,
        int page,
        int size,
        long total
) {
}
