package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Java 调用 FastAPI 投稿预检的稳定内部协议。 */
public record AiKnowledgePrecheckRequest(
        String title,
        String content,
        @JsonProperty("source_type") String sourceType
) { }
