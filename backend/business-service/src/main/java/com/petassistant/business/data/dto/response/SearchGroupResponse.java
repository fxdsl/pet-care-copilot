package com.petassistant.business.data.dto.response;

import java.util.List;

/** 按内容类型分组的搜索结果及该类型总命中数。 */
public record SearchGroupResponse(
        String type,
        long total,
        List<SearchResultItemResponse> items
) { }
