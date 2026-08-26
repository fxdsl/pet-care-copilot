package com.petassistant.business.data.dto.response;

import java.util.List;

/** 私信会话分页响应，桌面端用于左栏，移动端用于会话列表页。 */
public record DirectConversationPageResponse(
        List<DirectConversationResponse> items,
        int page,
        int size,
        long total
) {
}
