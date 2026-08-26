package com.petassistant.business.data.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 知识投稿数据库实体。原文、清洗结果、审核状态和已发布文档关联均由 MySQL 保存。
 */
public record KnowledgeSubmissionEntity(
        String id,
        String sourceType,
        String sourceBusinessId,
        String authorUserId,
        String title,
        String sourceName,
        String sourceAuthor,
        String sourceUrl,
        String fileName,
        String documentType,
        String petType,
        String category,
        String originalContent,
        String cleanedContent,
        String contentChecksum,
        String consentStatus,
        String status,
        String riskLevel,
        String riskLabels,
        String aiSummary,
        BigDecimal qualityScore,
        int currentVersion,
        String reviewerUserId,
        Instant reviewedAt,
        String publishedDocumentId,
        Instant sourcePublishedAt,
        Instant publishedAt,
        Instant expiresAt,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) { }
