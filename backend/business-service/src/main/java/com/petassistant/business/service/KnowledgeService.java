package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.data.dto.internal.AiKnowledgeChunk;
import com.petassistant.business.data.dto.internal.AiKnowledgePreprocessResponse;
import com.petassistant.business.data.dto.internal.AiPdfExtractResponse;
import com.petassistant.business.data.dto.internal.KnowledgeDocumentProjection;
import com.petassistant.business.data.dto.internal.KnowledgeReindexDocument;
import com.petassistant.business.data.dto.internal.KnowledgeProcessInput;
import com.petassistant.business.data.dto.request.PdfExtractRequest;
import com.petassistant.business.data.dto.response.KnowledgeDocumentSummaryResponse;
import com.petassistant.business.data.dto.response.KnowledgeReindexResponse;
import com.petassistant.business.data.dto.response.PdfExtractResponse;
import com.petassistant.business.data.entity.KnowledgeChunkEntity;
import com.petassistant.business.data.entity.KnowledgePublicationEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionEntity;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 已审核知识发布服务：负责 PDF 预览、向量发布、checksum 去重和模型版本重建。
 */
@Service
public class KnowledgeService {

    private final KnowledgeAiClient knowledgeAiClient;
    private final KnowledgeMapper mapper;
    private final ObjectMapper objectMapper;

    /** 注入 AI 客户端、MyBatis Mapper 和 JSON 序列化器。 */
    public KnowledgeService(KnowledgeAiClient knowledgeAiClient, KnowledgeMapper mapper, ObjectMapper objectMapper) {
        this.knowledgeAiClient = knowledgeAiClient;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 把人工审核通过的投稿发布为 RAG 文档。投稿原文先由当前向量模型重新处理，
     * 文档、全部分块和旧版本失效处于同一个 MySQL 事务。
     */
    @Transactional
    public String publishApproved(KnowledgeSubmissionEntity submission, String trustLevel) {
        if (!"PUBLISHING".equals(submission.status())) {
            throw new IllegalStateException("只有 PUBLISHING 投稿可以发布");
        }
        if (submission.expiresAt() != null && !submission.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("资料有效期已过，不能发布");
        }
        KnowledgeProcessInput request = new KnowledgeProcessInput(
                submission.title(), submission.sourceUrl(), submission.sourceName(), submission.petType(),
                submission.category(), submission.cleanedContent() == null
                        ? submission.originalContent() : submission.cleanedContent(),
                submission.fileName(), submission.documentType(), 800, 120
        );
        validateImport(request);
        AiKnowledgePreprocessResponse processed = knowledgeAiClient.preprocess(request);
        KnowledgeDocumentProjection duplicate = mapper.findByChecksum(processed.checksum());
        if (duplicate != null) {
            throw new IllegalArgumentException("相同内容已存在于知识库：" + duplicate.title());
        }

        Instant now = Instant.now();
        String documentId = UUID.randomUUID().toString();
        mapper.supersedePublishedBySubmission(submission.id(), now);
        mapper.insertPublishedDocument(new KnowledgePublicationEntity(
                documentId, submission.id(), submission.title(), submission.sourceType(),
                submission.sourceBusinessId(), submission.sourceUrl(), submission.sourceName(),
                submission.sourceAuthor(), submission.authorUserId(), submission.fileName(),
                submission.documentType(), submission.petType(), submission.category(),
                processed.cleanedContent(), processed.checksum(), trustLevel, submission.qualityScore(),
                submission.consentStatus(), submission.reviewerUserId(), submission.reviewedAt(),
                submission.currentVersion(), now, submission.expiresAt()
        ));
        mapper.insertChunks(toEntities(documentId, processed.chunks(), now));
        return documentId;
    }

    /** 撤回只改变检索资格，历史文档与分块继续保留供管理员审计。 */
    @Transactional
    public void withdrawPublished(String submissionId) {
        mapper.withdrawPublishedBySubmission(submissionId, Instant.now());
    }

    /** PDF 提取是只读预览步骤，用户确认导入前不会产生数据库记录。 */
    public PdfExtractResponse extractPdf(PdfExtractRequest request) {
        AiPdfExtractResponse result = knowledgeAiClient.extractPdf(request);
        return new PdfExtractResponse(
                result.fileName(),
                result.status(),
                result.extractionMode(),
                result.pageCount(),
                result.charCount(),
                result.content(),
                result.preview(),
                result.pages().stream()
                        .map(page -> new PdfExtractResponse.PdfPageResponse(
                                page.pageNumber(), page.text(), page.charCount()
                        ))
                        .toList()
        );
    }

    /** 查询最近文档、文件类型、模型和向量化完成度。 */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentSummaryResponse> list(int limit) {
        return mapper.findRecent(Math.min(Math.max(limit, 1), 100));
    }

    /**
     * 显式重建全部 READY 文档，确保数据库不再混用已淘汰的向量模型。
     * 该操作在单一事务中替换分块，任一文档失败则全部回滚。
     */
    @Transactional
    public KnowledgeReindexResponse reindexAll() {
        List<KnowledgeReindexDocument> documents = mapper.findAllForReindex();
        int totalChunks = 0;
        String modelName = null;
        Instant now = Instant.now();
        for (KnowledgeReindexDocument document : documents) {
            KnowledgeProcessInput request = new KnowledgeProcessInput(
                    document.title(), document.sourceUrl(), document.sourceName(), document.petType(),
                    document.category(), document.content(), document.fileName(), document.documentType(), 800, 120
            );
            AiKnowledgePreprocessResponse processed = knowledgeAiClient.preprocess(request);
            mapper.deleteChunksByDocumentId(document.id());
            mapper.insertChunks(toEntities(document.id(), processed.chunks(), now));
            totalChunks += processed.chunks().size();
            modelName = processed.chunks().get(0).embeddingModel();
        }
        return new KnowledgeReindexResponse(documents.size(), totalChunks, modelName);
    }

    /** 校验导入参数和 PDF 元数据的一致性。 */
    private static void validateImport(KnowledgeProcessInput request) {
        if (request.resolvedChunkOverlap() >= request.resolvedChunkSize()) {
            throw new IllegalArgumentException("分块重叠长度必须小于分块大小");
        }
        if (!List.of("TEXT", "PDF").contains(request.resolvedDocumentType())) {
            throw new IllegalArgumentException("documentType 只允许 TEXT 或 PDF");
        }
        if ("PDF".equals(request.resolvedDocumentType())
                && (request.fileName() == null || !request.fileName().toLowerCase().endsWith(".pdf"))) {
            throw new IllegalArgumentException("PDF 导入必须包含 .pdf 文件名");
        }
    }

    /** 把 FastAPI 分块转换为独立数据库实体。 */
    private List<KnowledgeChunkEntity> toEntities(
            String documentId,
            List<AiKnowledgeChunk> chunks,
            Instant now
    ) {
        return chunks.stream().map(chunk -> new KnowledgeChunkEntity(
                UUID.randomUUID().toString(),
                documentId,
                chunk.chunkIndex(),
                chunk.content(),
                chunk.charCount(),
                chunk.tokenEstimate(),
                serializeEmbedding(chunk),
                chunk.embeddingModel(),
                chunk.embedding().size(),
                chunk.pageStart(),
                chunk.pageEnd(),
                now,
                now
        )).toList();
    }

    /** 序列化失败必须抛错，让 Spring 回滚本次完整导入。 */
    private String serializeEmbedding(AiKnowledgeChunk chunk) {
        try {
            return objectMapper.writeValueAsString(chunk.embedding());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化知识分块向量", error);
        }
    }

}
