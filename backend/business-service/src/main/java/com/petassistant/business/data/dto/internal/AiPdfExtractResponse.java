package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI PDF 提取内部响应，扫描件以 OCR_REQUIRED 明确返回。 */
public record AiPdfExtractResponse(
        @JsonProperty("file_name") String fileName,
        String status,
        @JsonProperty("extraction_mode") String extractionMode,
        @JsonProperty("page_count") int pageCount,
        @JsonProperty("char_count") int charCount,
        String content,
        String preview,
        List<AiPdfPage> pages
) {
}
