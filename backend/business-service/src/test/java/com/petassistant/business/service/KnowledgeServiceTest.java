package com.petassistant.business.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.data.dto.internal.AiKnowledgeChunk;
import com.petassistant.business.data.dto.internal.AiKnowledgePreprocessResponse;
import com.petassistant.business.data.dto.internal.KnowledgeDocumentProjection;
import com.petassistant.business.data.entity.KnowledgeChunkEntity;
import com.petassistant.business.data.entity.KnowledgePublicationEntity;
import com.petassistant.business.data.entity.KnowledgeSubmissionEntity;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第十一周审核后知识发布、来源版本和向量分块测试。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private KnowledgeAiClient knowledgeAiClient;
    @Mock private KnowledgeMapper mapper;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(knowledgeAiClient, mapper, new ObjectMapper());
    }

    /** 只有 PUBLISHING 投稿能写入 APPROVED 文档和带 PDF 页码的向量分块。 */
    @Test
    void shouldPublishApprovedVersionWithTraceableMetadata() {
        KnowledgeSubmissionEntity submission = submission();
        AiKnowledgePreprocessResponse processed = processed("a".repeat(64));
        when(knowledgeAiClient.preprocess(any())).thenReturn(processed);
        when(mapper.findByChecksum(processed.checksum())).thenReturn(null);

        String documentId = service.publishApproved(submission, "A");

        ArgumentCaptor<KnowledgePublicationEntity> document = ArgumentCaptor.forClass(KnowledgePublicationEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeChunkEntity>> chunks = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertPublishedDocument(document.capture());
        verify(mapper).insertChunks(chunks.capture());
        assertThat(document.getValue().id()).isEqualTo(documentId);
        assertThat(document.getValue().submissionId()).isEqualTo("submission-1");
        assertThat(document.getValue().trustLevel()).isEqualTo("A");
        assertThat(document.getValue().version()).isEqualTo(2);
        assertThat(chunks.getValue().get(0).pageStart()).isEqualTo(2);
    }

    /** MySQL checksum 最终去重命中时必须停止发布，不能生成第二份可召回文档。 */
    @Test
    void shouldRejectDuplicateContentBeforePublication() {
        AiKnowledgePreprocessResponse processed = processed("b".repeat(64));
        when(knowledgeAiClient.preprocess(any())).thenReturn(processed);
        when(mapper.findByChecksum(processed.checksum())).thenReturn(new KnowledgeDocumentProjection(
                "document-old", "已存在资料", processed.checksum(), 1, 1, "BAAI/bge-small-zh-v1.5"
        ));

        assertThatThrownBy(() -> service.publishApproved(submission(), "B"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("相同内容已存在");
        verify(mapper, never()).insertPublishedDocument(any());
    }

    private static AiKnowledgePreprocessResponse processed(String checksum) {
        return new AiKnowledgePreprocessResponse(
                "[PDF_PAGE:2]\n清洗后的正文", checksum,
                List.of(new AiKnowledgeChunk(
                        0, "清洗后的正文", 7, 4, Collections.nCopies(512, 0.01),
                        "BAAI/bge-small-zh-v1.5", 2, 2
                ))
        );
    }

    private static KnowledgeSubmissionEntity submission() {
        Instant now = Instant.now();
        return new KnowledgeSubmissionEntity(
                "submission-1", "ADMIN_UPLOAD", null, "admin-1", "幼猫手册", "权威机构", "兽医作者",
                "https://example.test/guide", "guide.pdf", "PDF", "CAT", "FEEDING",
                "[PDF_PAGE:2]\n原始正文", "[PDF_PAGE:2]\n清洗后的正文", "c".repeat(64),
                "NOT_REQUIRED", "PUBLISHING", "LOW", "", "摘要", null, 2,
                "admin-2", now, null, now, null, now.plusSeconds(86400), null, now, now
        );
    }
}
