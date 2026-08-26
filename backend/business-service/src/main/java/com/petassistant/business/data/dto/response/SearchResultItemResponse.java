package com.petassistant.business.data.dto.response;

import java.time.Instant;
import java.util.List;

/** 统一搜索结果项；前端根据 matchedFields 安全地高亮普通文本。 */
public record SearchResultItemResponse(
        String id,
        String type,
        String title,
        String snippet,
        String authorName,
        String avatarUrl,
        String sourceUrl,
        String routePath,
        String petType,
        String category,
        String trustLevel,
        Instant publishedAt,
        double score,
        List<String> matchedFields
) { }
