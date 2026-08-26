package com.petassistant.business.data.dto.response;

/** Redis ZSet 中的脱敏搜索趋势。 */
public record SearchTrendingResponse(String query, double score) { }
