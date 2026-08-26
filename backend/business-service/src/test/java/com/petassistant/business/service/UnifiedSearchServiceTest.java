package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.petassistant.business.data.mapper.SearchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OpenSearch 故障时必须保持公开权限条件并返回可解释的 MySQL 降级标记。 */
@ExtendWith(MockitoExtension.class)
class UnifiedSearchServiceTest {

    @Mock SearchMapper mapper;
    @Mock SearchIndexClient indexClient;
    @Mock KnowledgeAiClient aiClient;
    @Mock SearchCacheService cacheService;

    @Test
    void shouldFallbackToMySqlWithoutChangingRequestedType() {
        SearchProperties properties = new SearchProperties(
                true, "http://localhost:9200", "public-v1", 512,
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(2),
                true, "search.events", "search.index", "search.index"
        );
        UnifiedSearchService service = new UnifiedSearchService(
                mapper, indexClient, aiClient, cacheService, properties, new ObjectMapper()
        );
        SearchSourceDocument document = new SearchSourceDocument(
                "post-1", "POST", "幼猫喂养", "少量多餐", "alice", null, null,
                "CAT", null, null, Instant.now(), "/app/community?post=post-1"
        );
        when(cacheService.indexVersion()).thenReturn(3L);
        when(indexClient.search(anyString(), eq("POST"), any(), any(), any(), any(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new IllegalStateException("OpenSearch down"));
        when(mapper.findFallback(eq("幼猫"), eq("POST"), any(), any(), any(), any(), eq("LATEST"), eq(0), eq(10)))
                .thenReturn(List.of(document));
        when(mapper.countFallback(eq("幼猫"), eq("POST"), any(), any(), any(), any())).thenReturn(1L);

        var response = service.search(
                "user-1", "幼猫", "POST", null, null, null, "ALL", "LATEST", 0, 10
        );

        assertThat(response.backend()).isEqualTo("MYSQL");
        assertThat(response.degraded()).isTrue();
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.groups()).singleElement().satisfies(group -> {
            assertThat(group.type()).isEqualTo("POST");
            assertThat(group.items()).singleElement().extracting(item -> item.title()).isEqualTo("幼猫喂养");
        });
        verify(mapper).upsertHistory(any());
        verify(cacheService).recordTrending("幼猫");
    }
}
