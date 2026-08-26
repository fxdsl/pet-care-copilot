package com.petassistant.business.data.entity;

import java.time.Instant;

/** OpenSearch 全量重建任务的 MySQL 事实记录。 */
public record SearchIndexJobEntity(
        String id,
        String requestedBy,
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
