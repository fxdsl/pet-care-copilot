package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.petassistant.business.data.entity.SearchHistoryEntity;
import com.petassistant.business.data.entity.SearchIndexJobEntity;
import org.apache.ibatis.annotations.Param;

/** 统一搜索 MyBatis Mapper；MySQL 始终负责权限回源、历史和重建任务事实。 */
public interface SearchMapper {

    List<SearchSourceDocument> findFallback(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("petType") String petType,
            @Param("category") String category,
            @Param("trustLevel") String trustLevel,
            @Param("publishedAfter") Instant publishedAfter,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countFallback(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("petType") String petType,
            @Param("category") String category,
            @Param("trustLevel") String trustLevel,
            @Param("publishedAfter") Instant publishedAfter
    );

    List<String> findSuggestions(@Param("keyword") String keyword, @Param("limit") int limit);

    SearchSourceDocument findPostDocument(@Param("id") String id);

    SearchSourceDocument findKnowledgeDocument(@Param("id") String id);

    SearchSourceDocument findUserDocument(@Param("id") String id);

    SearchSourceDocument findTopicDocument(@Param("id") String id);

    List<SearchSourceDocument> findAllPublicDocuments();

    int upsertHistory(SearchHistoryEntity history);

    List<SearchHistoryEntity> findHistory(@Param("userId") String userId, @Param("limit") int limit);

    int deleteHistory(@Param("id") String id, @Param("userId") String userId);

    int deleteAllHistory(@Param("userId") String userId);

    int insertJob(SearchIndexJobEntity job);

    SearchIndexJobEntity findJob(@Param("id") String id);

    int startJob(@Param("id") String id, @Param("totalCount") int totalCount, @Param("startedAt") Instant startedAt);

    int completeJob(
            @Param("id") String id,
            @Param("indexedCount") int indexedCount,
            @Param("failedCount") int failedCount,
            @Param("completedAt") Instant completedAt
    );

    int failJob(
            @Param("id") String id,
            @Param("indexedCount") int indexedCount,
            @Param("failedCount") int failedCount,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") Instant completedAt
    );
}
