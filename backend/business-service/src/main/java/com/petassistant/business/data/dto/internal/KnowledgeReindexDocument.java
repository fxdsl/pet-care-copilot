package com.petassistant.business.data.dto.internal;

/** 从 MySQL 读取并交给 FastAPI 重建向量的完整文档投影。 */
public record KnowledgeReindexDocument(
        String id,
        String title,
        String sourceUrl,
        String sourceName,
        String fileName,
        String documentType,
        String petType,
        String category,
        String content
) {
}
