package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI 返回的知识分块，包含可直接写入 MySQL 的本地向量。
 */
public record AiKnowledgeChunk(
        @JsonProperty("chunk_index") int chunkIndex,
        String content,
        @JsonProperty("char_count") int charCount,
        @JsonProperty("token_estimate") int tokenEstimate,
        List<Double> embedding,
        @JsonProperty("embedding_model") String embeddingModel,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd
) {
}
