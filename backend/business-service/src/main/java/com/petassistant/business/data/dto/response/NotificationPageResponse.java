package com.petassistant.business.data.dto.response;

import java.util.List;

/** 分页通知响应。 */
public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long total
) {
}
