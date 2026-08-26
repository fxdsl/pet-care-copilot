package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发送给 FastAPI 的文档预处理请求，使用 Python 风格字段名保持服务契约清晰。
 */
public record AiKnowledgePreprocessRequest(
        String title,
        String content,
        @JsonProperty("chunk_size") int chunkSize,
        @JsonProperty("chunk_overlap") int chunkOverlap
) {
}
