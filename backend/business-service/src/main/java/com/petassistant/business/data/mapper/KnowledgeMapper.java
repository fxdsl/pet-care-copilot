package com.petassistant.business.data.mapper;

import java.util.List;

import com.petassistant.business.data.dto.internal.KnowledgeDocumentProjection;
import com.petassistant.business.data.dto.internal.KnowledgeReindexDocument;
import com.petassistant.business.data.dto.internal.AgentCandidateRow;
import com.petassistant.business.data.dto.response.KnowledgeDocumentSummaryResponse;
import com.petassistant.business.data.entity.KnowledgeChunkEntity;
import com.petassistant.business.data.entity.KnowledgePublicationEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 知识文档、分块和 Agent 候选查询的 MyBatis Mapper。
 */
public interface KnowledgeMapper {

    /** 按清洗后正文 checksum 查询去重投影。 */
    KnowledgeDocumentProjection findByChecksum(@Param("checksum") String checksum);

    /** 把已审核版本发布为唯一可检索文档。 */
    int insertPublishedDocument(KnowledgePublicationEntity document);

    /** 新版本发布前使旧版本退出召回，但保留文档与分块用于审计。 */
    int supersedePublishedBySubmission(@Param("submissionId") String submissionId, @Param("updatedAt") java.time.Instant updatedAt);

    /** 作者撤回投稿时立即让已发布文档退出 RAG。 */
    int withdrawPublishedBySubmission(@Param("submissionId") String submissionId, @Param("updatedAt") java.time.Instant updatedAt);

    /** 批量写入同一文档的知识分块和向量。 */
    int insertChunks(@Param("chunks") List<KnowledgeChunkEntity> chunks);

    /** 模型或分块算法升级时删除指定文档的旧分块，随后在同一事务中重建。 */
    int deleteChunksByDocumentId(@Param("documentId") String documentId);

    /** 查询最近导入的知识文档及其向量化进度。 */
    List<KnowledgeDocumentSummaryResponse> findRecent(@Param("limit") int limit);

    /** 查询全部知识正文，供用户显式触发专业向量重建。 */
    List<KnowledgeReindexDocument> findAllForReindex();

    /**
     * 读取有限数量的已向量化候选分块。
     * 相似度计算由 FastAPI 完成，MySQL 在本阶段只负责可靠保存与元数据过滤。
     */
    List<AgentCandidateRow> findAgentCandidates(
            @Param("petType") String petType,
            @Param("category") String category,
            @Param("limit") int limit
    );
}
