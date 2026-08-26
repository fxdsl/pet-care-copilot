package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI 文档预处理响应，正文清洗、分块与向量化在一次调用中完成。
 */
public record AiKnowledgePreprocessResponse(
        @JsonProperty("cleaned_content") String cleanedContent,
        String checksum,
        List<AiKnowledgeChunk> chunks
) {
}
