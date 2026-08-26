package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 返回的真实工具召回来源，来源字段不由大模型生成。 */
public record AiAgentSource(
        String title,
        String url,
        @JsonProperty("chunk_id") String chunkId,
        double score,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd
) {
}
