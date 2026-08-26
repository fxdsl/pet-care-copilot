package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 当前用户可管理的搜索历史。 */
public record SearchHistoryResponse(
        String id,
        String query,
        String filtersJson,
        long resultCount,
        int searchCount,
        Instant lastSearchedAt
) { }
