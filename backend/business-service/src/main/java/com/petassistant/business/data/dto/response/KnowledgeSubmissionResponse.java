package com.petassistant.business.data.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 用户与管理端共用的投稿详情；原文只在鉴权后的投稿/审核接口返回。 */
public record KnowledgeSubmissionResponse(
        String id,
        String sourceType,
        String sourceBusinessId,
        String authorUserId,
        String authorName,
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
        String consentStatus,
        String status,
        String riskLevel,
        List<String> riskLabels,
        String aiSummary,
        BigDecimal qualityScore,
        int currentVersion,
        String reviewerUserId,
        String reviewerName,
        String publishedDocumentId,
        Instant sourcePublishedAt,
        Instant reviewedAt,
        Instant publishedAt,
        Instant expiresAt,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        List<KnowledgeReviewRecordResponse> timeline
) { }
