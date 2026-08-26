package com.petassistant.business.data.dto.response;

import java.time.LocalDate;

/** 管理端社区近似 UV 与治理积压摘要。 */
public record CommunityAnalyticsResponse(
        LocalDate date,
        long approximateFeedUv,
        long pendingReports
) { }
