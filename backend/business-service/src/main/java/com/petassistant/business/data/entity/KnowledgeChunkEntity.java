package com.petassistant.business.data.entity;

import java.time.Instant;

/**
 * 知识分块数据库实体。
 * 向量使用 JSON 保存，以兼容当前本机 MySQL 8.0 Community 版本。
 */
public record KnowledgeChunkEntity(
        String id,
        String documentId,
        int chunkIndex,
        String content,
        int charCount,
        int tokenEstimate,
        String embeddingJson,
        String embeddingModel,
        Integer embeddingDimensions,
        Integer pageStart,
        Integer pageEnd,
        Instant embeddedAt,
        Instant createdAt
) {
}
