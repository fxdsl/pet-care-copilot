package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.internal.AiSearchEmbeddingResponse;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.petassistant.business.data.mapper.SearchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 全量索引重建必须限制向量文本，并把部分失败暴露为 FAILED。 */
@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    @Mock SearchMapper mapper;
    @Mock SearchIndexClient indexClient;
    @Mock KnowledgeAiClient aiClient;
    @Mock SearchCacheService cacheService;
    @Mock OutboxService outboxService;
    @Mock RedissonClient redissonClient;
    @Mock RLock lock;

    private SearchIndexService service;

    @BeforeEach
    void setUp() throws InterruptedException {
        SearchProperties properties = new SearchProperties(
                true, "http://localhost:9200", "public-v1", 512,
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(2),
                true, "search.events", "search.index", "search.index"
        );
        service = new SearchIndexService(
                mapper, indexClient, aiClient, cacheService, outboxService, redissonClient, properties
        );
        when(redissonClient.getLock("search:index:rebuild")).thenReturn(lock);
        when(lock.tryLock(0, 30, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void shouldLimitEmbeddingTextByUnicodeCodePoints() {
        SearchSourceDocument document = document("knowledge-1", "KNOWLEDGE", "幼猫", "养".repeat(10_500) + "🐶");
        when(mapper.findAllPublicDocuments()).thenReturn(List.of(document));
        when(mapper.startJob(eq("job-1"), eq(1), any())).thenReturn(1);
        when(aiClient.embedForSearch(anyString(), eq(true)))
                .thenReturn(new AiSearchEmbeddingResponse(List.of(0.1D), "local-bge", 1));
        when(cacheService.addPublicDocument("KNOWLEDGE", "knowledge-1")).thenReturn(true);

        service.process(rebuildEvent("job-1"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(aiClient).embedForSearch(text.capture(), eq(true));
        assertThat(text.getValue().codePointCount(0, text.getValue().length())).isEqualTo(10_000);
        assertThat(Character.isHighSurrogate(text.getValue().charAt(text.getValue().length() - 1))).isFalse();
        verify(mapper).completeJob(eq("job-1"), eq(1), eq(0), any());
        verify(mapper, never()).failJob(anyString(), any(Integer.class), any(Integer.class), anyString(), any());
    }

    @Test
    void shouldFailJobAndDeleteIncompleteIndexWhenAnyDocumentFails() {
        SearchSourceDocument document = document("post-1", "POST", "幼猫喂养", "少量多餐");
        when(mapper.findAllPublicDocuments()).thenReturn(List.of(document));
        when(mapper.startJob(eq("job-2"), eq(1), any())).thenReturn(1);
        when(aiClient.embedForSearch(anyString(), eq(true)))
                .thenReturn(new AiSearchEmbeddingResponse(List.of(0.1D), "local-bge", 1));
        doThrow(new IllegalStateException("mapper_parsing_exception"))
                .when(indexClient).upsert(eq(document), anyList());

        service.process(rebuildEvent("job-2"));

        verify(indexClient).deleteIndex();
        verify(mapper).failJob(
                eq("job-2"), eq(0), eq(1), contains("POST:post-1"), any()
        );
        verify(mapper, never()).completeJob(anyString(), any(Integer.class), any(Integer.class), any());
    }

    private static CommunityEventPayload rebuildEvent(String jobId) {
        return new CommunityEventPayload("event-1", "SEARCH_REBUILD_REQUESTED", jobId, "admin-1", Instant.now());
    }

    private static SearchSourceDocument document(String id, String type, String title, String content) {
        return new SearchSourceDocument(
                id, type, title, content, "alice", null, null,
                "CAT", null, null, Instant.parse("2026-08-25T10:20:53.433863Z"),
                "/app/community"
        );
    }
}
