package com.petassistant.business.data.dto.internal;

/** 审核发布和重建向量时使用的内部预处理输入，不是可绕过审核的 HTTP DTO。 */
public record KnowledgeProcessInput(
        String title,
        String sourceUrl,
        String sourceName,
        String petType,
        String category,
        String content,
        String fileName,
        String documentType,
        Integer chunkSize,
        Integer chunkOverlap
) {
    public int resolvedChunkSize() { return chunkSize == null ? 800 : chunkSize; }
    public int resolvedChunkOverlap() { return chunkOverlap == null ? 120 : chunkOverlap; }
    public String resolvedDocumentType() {
        return documentType == null || documentType.isBlank() ? "TEXT" : documentType.trim().toUpperCase();
    }
}
