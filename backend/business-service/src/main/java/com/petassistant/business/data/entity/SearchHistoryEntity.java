package com.petassistant.business.data.entity;

import java.time.Instant;

/** 当前用户的一条搜索历史事实；热词统计不从该表直接公开原始敏感查询。 */
public record SearchHistoryEntity(
        String id,
        String userId,
        String queryText,
        String normalizedQuery,
        String queryHash,
        String filtersJson,
        long resultCount,
        int searchCount,
        Instant lastSearchedAt,
        Instant createdAt,
        Instant updatedAt
) { }
