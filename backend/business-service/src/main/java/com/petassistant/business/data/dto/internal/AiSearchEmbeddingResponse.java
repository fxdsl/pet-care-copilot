package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 返回的本地 BGE 向量和模型元数据。 */
public record AiSearchEmbeddingResponse(
        List<Double> embedding,
        @JsonProperty("embedding_model") String embeddingModel,
        int dimensions
) { }
