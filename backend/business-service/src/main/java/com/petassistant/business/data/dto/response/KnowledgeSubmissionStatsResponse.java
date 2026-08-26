package com.petassistant.business.data.dto.response;

/** 管理工作台顶部的待审、已发布和高风险数量。 */
public record KnowledgeSubmissionStatsResponse(
        long pendingReview,
        long published,
        long rejected,
        long highRisk
) { }
