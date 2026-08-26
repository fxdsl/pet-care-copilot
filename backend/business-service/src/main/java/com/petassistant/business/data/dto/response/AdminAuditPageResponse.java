package com.petassistant.business.data.dto.response;

import java.util.List;

/** 管理员审计分页响应。 */
public record AdminAuditPageResponse(
        List<AdminAuditResponse> items,
        int page,
        int size,
        long total
) {
}
