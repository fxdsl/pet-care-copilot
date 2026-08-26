package com.petassistant.business.data.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** 审核通过后写入可检索知识文档所需的完整发布快照。 */
public record KnowledgePublicationEntity(
        String id,
        String submissionId,
        String title,
        String sourceType,
        String sourceBusinessId,
        String sourceUrl,
        String sourceName,
        String sourceAuthor,
        String authorUserId,
        String fileName,
        String documentType,
        String petType,
        String category,
        String content,
        String contentChecksum,
        String trustLevel,
        BigDecimal qualityScore,
        String consentStatus,
        String reviewerUserId,
        Instant reviewedAt,
        int version,
        Instant publishedAt,
        Instant expiresAt
) { }
