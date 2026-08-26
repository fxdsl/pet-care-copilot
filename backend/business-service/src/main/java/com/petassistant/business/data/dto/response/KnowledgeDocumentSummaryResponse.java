package com.petassistant.business.data.dto.response;

import java.time.Instant;

/**
 * 知识文档列表投影，同时展示分块数量与已向量化分块数量。
 */
public record KnowledgeDocumentSummaryResponse(
        String id,
        String title,
        String sourceName,
        String fileName,
        String documentType,
        String petType,
        String category,
        String status,
        int chunkCount,
        int embeddedChunkCount,
        String embeddingModel,
        Instant createdAt
) {
}
