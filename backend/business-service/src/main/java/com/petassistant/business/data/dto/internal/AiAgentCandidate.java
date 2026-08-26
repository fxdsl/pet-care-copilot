package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 发送给 FastAPI Agent 知识工具的单个只读候选分块。 */
public record AiAgentCandidate(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("document_id") String documentId,
        String title,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("chunk_index") int chunkIndex,
        String content,
        List<Double> embedding,
        @JsonProperty("embedding_model") String embeddingModel,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd
) {
}
