package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Java 调用 FastAPI 免费本地搜索向量接口的请求。 */
public record AiSearchEmbeddingRequest(
        String text,
        @JsonProperty("mode") String mode
) { }
