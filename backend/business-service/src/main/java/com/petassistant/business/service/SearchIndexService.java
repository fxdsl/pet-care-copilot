package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.petassistant.business.data.dto.response.SearchIndexJobResponse;
import com.petassistant.business.data.entity.SearchIndexJobEntity;
import com.petassistant.business.data.mapper.SearchMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OpenSearch 副本的增量消费、全量重建和任务状态编排。 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final int MAX_EMBEDDING_TEXT_CODE_POINTS = 10_000;

    private final SearchMapper mapper;
    private final SearchIndexClient indexClient;
    private final KnowledgeAiClient aiClient;
    private final SearchCacheService cacheService;
    private final OutboxService outboxService;
    private final RedissonClient redissonClient;
    private final SearchProperties properties;

    public SearchIndexService(
            SearchMapper mapper,
            SearchIndexClient indexClient,
            KnowledgeAiClient aiClient,
            SearchCacheService cacheService,
            OutboxService outboxService,
            RedissonClient redissonClient,
            SearchProperties properties
    ) {
        this.mapper = mapper;
        this.indexClient = indexClient;
        this.aiClient = aiClient;
        this.cacheService = cacheService;
        this.outboxService = outboxService;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    /** 创建 MySQL 任务事实与 Outbox 事件，HTTP 请求无需等待全量向量化。 */
    @Transactional
    public SearchIndexJobResponse requestRebuild(String administratorId) {
        Instant now = Instant.now();
        String jobId = UUID.randomUUID().toString();
        SearchIndexJobEntity job = new SearchIndexJobEntity(
                jobId, administratorId, properties.indexName(), cacheService.indexVersion() + 1,
                "PENDING", 0, 0, 0, null, null, null, now, now
        );
        mapper.insertJob(job);
        outboxService.record("SEARCH_INDEX", jobId, "SEARCH_REBUILD_REQUESTED", administratorId);
        return toResponse(job);
    }

    public SearchIndexJobResponse job(String jobId) {
        SearchIndexJobEntity job = mapper.findJob(jobId);
        if (job == null) throw new IllegalArgumentException("搜索索引任务不存在");
        return toResponse(job);
    }

    /** 消费幂等事件；最终公开资格总是重新从 MySQL 查询，而不是相信消息内容。 */
    public void process(CommunityEventPayload event) {
        switch (event.eventType()) {
            case "SEARCH_REBUILD_REQUESTED" -> rebuild(event.aggregateId());
            case "SEARCH_POST_UPSERT" -> upsertOrDelete("POST", event.aggregateId());
            case "SEARCH_KNOWLEDGE_UPSERT" -> upsertOrDelete("KNOWLEDGE", event.aggregateId());
            case "SEARCH_USER_UPSERT" -> upsertOrDelete("USER", event.aggregateId());
            case "SEARCH_TOPIC_UPSERT" -> upsertOrDelete("TOPIC", event.aggregateId());
            case "SEARCH_POST_DELETE" -> delete("POST", event.aggregateId());
            case "SEARCH_KNOWLEDGE_DELETE" -> delete("KNOWLEDGE", event.aggregateId());
            case "SEARCH_USER_DELETE" -> delete("USER", event.aggregateId());
            case "SEARCH_TOPIC_DELETE" -> delete("TOPIC", event.aggregateId());
            default -> {
                // 同一队列只绑定搜索路由，未知事件保持兼容并安全忽略。
            }
        }
    }

    private void upsertOrDelete(String type, String id) {
        SearchSourceDocument source = findPublicDocument(type, id);
        if (source == null) {
            delete(type, id);
            return;
        }
        indexClient.upsert(source, embedding(source));
        cacheService.addPublicDocument(type, id);
        cacheService.incrementIndexVersion();
    }

    private void delete(String type, String id) {
        if (!cacheService.mightContainPublicDocument(type, id)) return;
        indexClient.delete(type, id);
        cacheService.incrementIndexVersion();
    }

    /** Redisson 分布式锁保证多实例只能有一个全量重建执行者。 */
    private void rebuild(String jobId) {
        RLock lock = redissonClient.getLock("search:index:rebuild");
        boolean acquired = false;
        boolean indexRecreated = false;
        int indexed = 0;
        int failed = 0;
        try {
            acquired = lock.tryLock(0, 30, TimeUnit.MINUTES);
            if (!acquired) throw new IllegalStateException("已有搜索索引重建任务正在执行");
            List<SearchSourceDocument> documents = mapper.findAllPublicDocuments();
            if (mapper.startJob(jobId, documents.size(), Instant.now()) == 0) return;
            indexClient.recreateIndex();
            indexRecreated = true;
            cacheService.resetBloom();
            boolean bloomComplete = true;
            String firstFailure = null;
            for (SearchSourceDocument document : documents) {
                try {
                    indexClient.upsert(document, embedding(document));
                    bloomComplete &= cacheService.addPublicDocument(document.documentType(), document.id());
                    indexed++;
                } catch (RuntimeException error) {
                    failed++;
                    bloomComplete = false;
                    String documentFailure = document.documentType() + ":" + document.id()
                            + " 索引失败：" + safeError(error);
                    if (firstFailure == null) firstFailure = documentFailure;
                    log.warn("Search rebuild document failed, jobId={}, document={}: {}",
                            jobId, document.documentType() + ":" + document.id(), error.toString());
                }
            }
            cacheService.incrementIndexVersion();
            if (failed > 0) {
                String failure = discardIncompleteIndex(firstFailure == null ? "搜索索引重建存在失败文档" : firstFailure);
                mapper.failJob(jobId, indexed, failed, failure, Instant.now());
                return;
            }
            if (bloomComplete) cacheService.markBloomReady();
            mapper.completeJob(jobId, indexed, 0, Instant.now());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (indexRecreated) cacheService.incrementIndexVersion();
            mapper.failJob(jobId, indexed, failed, "索引重建线程被中断", Instant.now());
            throw new IllegalStateException("索引重建线程被中断", error);
        } catch (RuntimeException error) {
            String failure = indexRecreated ? discardIncompleteIndex(safeError(error)) : safeError(error);
            if (indexRecreated) cacheService.incrementIndexVersion();
            mapper.failJob(jobId, indexed, failed, failure, Instant.now());
            throw error;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private SearchSourceDocument findPublicDocument(String type, String id) {
        return switch (type) {
            case "POST" -> mapper.findPostDocument(id);
            case "KNOWLEDGE" -> mapper.findKnowledgeDocument(id);
            case "USER" -> mapper.findUserDocument(id);
            case "TOPIC" -> mapper.findTopicDocument(id);
            default -> null;
        };
    }

    private List<Double> embedding(SearchSourceDocument source) {
        return aiClient.embedForSearch(embeddingText(source), true).embedding();
    }

    /**
     * FastAPI 搜索向量契约最多接收 10,000 个 Unicode 码点。这里只截断向量输入，
     * OpenSearch 的关键词字段与 MySQL 原文保持完整，不会丢失可展示内容。
     */
    static String embeddingText(SearchSourceDocument source) {
        String title = source.title() == null ? "" : source.title();
        String content = source.content() == null ? "" : source.content();
        String text = title + "\n" + content;
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints <= MAX_EMBEDDING_TEXT_CODE_POINTS) return text;
        int end = text.offsetByCodePoints(0, MAX_EMBEDDING_TEXT_CODE_POINTS);
        return text.substring(0, end);
    }

    /** 删除失败重建产生的空/部分索引，让统一搜索重新走权限等价的 MySQL 降级。 */
    private String discardIncompleteIndex(String failure) {
        try {
            indexClient.deleteIndex();
            return failure;
        } catch (RuntimeException cleanupError) {
            log.error("Failed to delete incomplete OpenSearch index: {}", cleanupError.toString());
            return limitError(failure + "；清理不完整索引失败：" + safeError(cleanupError));
        }
    }

    private String safeError(RuntimeException error) {
        String value = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return limitError(value);
    }

    private String limitError(String value) {
        return value.length() <= 900 ? value : value.substring(0, 900);
    }

    private SearchIndexJobResponse toResponse(SearchIndexJobEntity job) {
        return new SearchIndexJobResponse(
                job.id(), job.indexName(), job.indexVersion(), job.status(), job.totalCount(),
                job.indexedCount(), job.failedCount(), job.errorMessage(), job.startedAt(),
                job.completedAt(), job.createdAt(), job.updatedAt()
        );
    }
}
