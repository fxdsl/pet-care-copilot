package com.petassistant.business.data.dto.internal;

/**
 * MyBatis 内部查询投影，用于内容去重和判断旧分块是否需要补充向量。
 */
public record KnowledgeDocumentProjection(
        String id,
        String title,
        String checksum,
        int chunkCount,
        int embeddedChunkCount,
        String embeddingModel
) {
}
