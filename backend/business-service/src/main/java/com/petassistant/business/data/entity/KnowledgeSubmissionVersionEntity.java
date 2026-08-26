package com.petassistant.business.data.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** 投稿每次提交时的不可变快照，保证审核结论能追溯到确切正文。 */
public record KnowledgeSubmissionVersionEntity(
        String id,
        String submissionId,
        int version,
        String title,
        String originalContent,
        String cleanedContent,
        String contentChecksum,
        String aiSummary,
        String riskLevel,
        String riskLabels,
        BigDecimal qualityScore,
        Instant createdAt
) { }
