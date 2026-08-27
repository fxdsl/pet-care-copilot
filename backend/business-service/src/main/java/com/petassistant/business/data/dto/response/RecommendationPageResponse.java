package com.petassistant.business.data.dto.response;

import java.util.List;

/** 个性化推荐分页。 */
public record RecommendationPageResponse(List<RecommendationItemResponse> items, int page, int size, long total) { }
