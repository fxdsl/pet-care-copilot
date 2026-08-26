package com.petassistant.business.data.dto.internal;

/**
 * MyBatis 为 Agent 知识工具读取的候选分块，包含文档元数据和序列化向量。
 */
public record AgentCandidateRow(
        String chunkId,
        String documentId,
        String title,
        String sourceName,
        String sourceUrl,
        String fileName,
        String petType,
        String category,
        int chunkIndex,
        String content,
        String embeddingJson,
        String embeddingModel,
        Integer pageStart,
        Integer pageEnd
) {
}
