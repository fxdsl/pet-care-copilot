package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 管理员查看全量索引重建进度的响应。 */
public record SearchIndexJobResponse(
        String id,
        String indexName,
        long indexVersion,
        String status,
        int totalCount,
        int indexedCount,
        int failedCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) { }
