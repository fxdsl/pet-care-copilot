package com.petassistant.business.data.dto.response;

import java.util.List;

/** 网页可直接展示的 PDF 预览结果。 */
public record PdfExtractResponse(
        String fileName,
        String status,
        String extractionMode,
        int pageCount,
        int charCount,
        String content,
        String preview,
        List<PdfPageResponse> pages
) {
    /** 单页预览数据。 */
    public record PdfPageResponse(int pageNumber, String text, int charCount) {
    }
}
