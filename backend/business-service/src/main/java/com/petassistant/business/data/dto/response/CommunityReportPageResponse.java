package com.petassistant.business.data.dto.response;

import java.util.List;

/** 管理端举报分页响应。 */
public record CommunityReportPageResponse(
        java.util.List<CommunityReportResponse> items,
        int page,
        int size,
        long total
) { }
