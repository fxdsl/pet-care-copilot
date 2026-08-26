package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 返回的单页 PDF 文字与字符数。 */
public record AiPdfPage(
        @JsonProperty("page_number") int pageNumber,
        String text,
        @JsonProperty("char_count") int charCount
) {
}
