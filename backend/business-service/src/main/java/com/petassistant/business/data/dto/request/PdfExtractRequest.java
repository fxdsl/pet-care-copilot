package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 浏览器传给 Spring Boot 的 PDF 文件名和 Base64 正文。 */
public record PdfExtractRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 21000000) String contentBase64
) {
}
