package com.petassistant.business.data.dto.response;

import java.util.List;

/** 管理端用户分页响应。 */
public record AdminUserPageResponse(
        List<AdminUserResponse> items,
        int page,
        int size,
        long total
) {
}
