package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Spring Boot 调用 FastAPI PDF 提取接口的内部请求。 */
public record AiPdfExtractRequest(
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_base64") String contentBase64
) {
}
