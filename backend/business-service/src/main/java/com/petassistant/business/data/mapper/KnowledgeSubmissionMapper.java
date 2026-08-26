package com.petassistant.business.data.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.entity.KnowledgeReviewRecordEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionVersionEntity;
import org.apache.ibatis.annotations.Param;

/** 第十一周投稿、版本和审核记录 MyBatis Mapper。 */
public interface KnowledgeSubmissionMapper {

    int insert(KnowledgeSubmissionEntity submission);

    int insertVersion(KnowledgeSubmissionVersionEntity version);

    int insertReviewRecord(KnowledgeReviewRecordEntity record);

    KnowledgeSubmissionEntity findById(@Param("id") String id);

    KnowledgeSubmissionEntity findBySource(
            @Param("sourceType") String sourceType,
            @Param("sourceBusinessId") String sourceBusinessId
    );

    List<KnowledgeSubmissionEntity> findMine(
            @Param("authorUserId") String authorUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countMine(@Param("authorUserId") String authorUserId);

    List<KnowledgeSubmissionEntity> findReviewPage(
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("sourceType") String sourceType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countReviewPage(
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("sourceType") String sourceType
    );

    long countByStatus(@Param("status") String status);

    long countHighRisk();

    List<KnowledgeReviewRecordEntity> findTimeline(@Param("submissionId") String submissionId);

    int completePrecheck(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("cleanedContent") String cleanedContent,
            @Param("contentChecksum") String contentChecksum,
            @Param("summary") String summary,
            @Param("riskLevel") String riskLevel,
            @Param("riskLabels") String riskLabels,
            @Param("qualityScore") BigDecimal qualityScore,
            @Param("updatedAt") Instant updatedAt
    );

    int updateVersionPrecheck(
            @Param("submissionId") String submissionId,
            @Param("version") int version,
            @Param("cleanedContent") String cleanedContent,
            @Param("contentChecksum") String contentChecksum,
            @Param("summary") String summary,
            @Param("riskLevel") String riskLevel,
            @Param("riskLabels") String riskLabels,
            @Param("qualityScore") BigDecimal qualityScore
    );

    int resetForResubmission(
            @Param("id") String id,
            @Param("authorUserId") String authorUserId,
            @Param("title") String title,
            @Param("content") String content,
            @Param("petType") String petType,
            @Param("category") String category,
            @Param("newVersion") int newVersion,
            @Param("updatedAt") Instant updatedAt
    );

    int approve(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("reviewerUserId") String reviewerUserId,
            @Param("reviewedAt") Instant reviewedAt
    );

    int reject(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("reviewerUserId") String reviewerUserId,
            @Param("reason") String reason,
            @Param("reviewedAt") Instant reviewedAt
    );

    int markPublished(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("documentId") String documentId,
            @Param("publishedAt") Instant publishedAt
    );

    int markFailed(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") Instant updatedAt
    );

    int withdrawOwned(
            @Param("id") String id,
            @Param("authorUserId") String authorUserId,
            @Param("updatedAt") Instant updatedAt
    );
}
