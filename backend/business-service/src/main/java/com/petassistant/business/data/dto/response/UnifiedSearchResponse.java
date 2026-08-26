package com.petassistant.business.data.dto.response;

import java.util.List;

/** 统一搜索响应，同时说明数据源、降级状态和当前索引版本。 */
public record UnifiedSearchResponse(
        String query,
        String type,
        int page,
        int size,
        long total,
        String backend,
        boolean degraded,
        long indexVersion,
        List<SearchGroupResponse> groups
) { }
