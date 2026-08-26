package com.petassistant.business.data.dto.response;

/** 一键重建全部知识向量的结果。 */
public record KnowledgeReindexResponse(
        int documentCount,
        int chunkCount,
        String embeddingModel
) {
}
