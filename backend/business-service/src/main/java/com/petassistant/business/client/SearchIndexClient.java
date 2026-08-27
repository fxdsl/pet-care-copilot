package com.petassistant.business.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.internal.OpenSearchHit;
import com.petassistant.business.data.dto.internal.OpenSearchPage;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 使用 OpenSearch REST API 维护公开搜索副本。MySQL 仍是权限和内容事实源，
 * 因此此客户端从不自行补齐或放宽公开范围。
 */
@Component
public class SearchIndexClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SearchProperties properties;

    public SearchIndexClient(
            @Qualifier("searchRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            SearchProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 轻量探活，用于系统状态和搜索降级判断。 */
    public boolean isAvailable() {
        if (!properties.enabled()) return false;
        try {
            restClient.get().uri("/").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    /** 删除旧索引并按当前向量维度创建新的公开索引。 */
    @CircuitBreaker(name = "openSearch")
    @Bulkhead(name = "openSearch", type = Bulkhead.Type.SEMAPHORE)
    public void recreateIndex() {
        deleteIndex();
        Map<String, Object> vectorMethod = Map.of(
                "name", "hnsw", "space_type", "cosinesimil", "engine", "lucene"
        );
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", Map.of("type", "keyword"));
        fields.put("documentType", Map.of("type", "keyword"));
        fields.put("title", Map.of("type", "text", "analyzer", "standard"));
        fields.put("content", Map.of("type", "text", "analyzer", "standard"));
        fields.put("authorName", Map.of("type", "text", "analyzer", "standard"));
        fields.put("avatarUrl", Map.of("type", "keyword", "index", false));
        fields.put("sourceUrl", Map.of("type", "keyword", "index", false));
        fields.put("routePath", Map.of("type", "keyword", "index", false));
        fields.put("petType", Map.of("type", "keyword"));
        fields.put("category", Map.of("type", "keyword"));
        fields.put("trustLevel", Map.of("type", "keyword"));
        fields.put("publishedAt", Map.of("type", "date"));
        fields.put("embedding", Map.of(
                "type", "knn_vector",
                "dimension", properties.embeddingDimensions(),
                "method", vectorMethod
        ));
        Map<String, Object> body = Map.of(
                "settings", Map.of("index", Map.of("knn", true)),
                "mappings", Map.of("properties", fields)
        );
        restClient.put().uri("/{index}", properties.indexName()).body(body)
                .retrieve().toBodilessEntity();
    }

    /** 删除搜索副本；失败重建时移除不完整索引，让查询安全降级到 MySQL。 */
    public void deleteIndex() {
        try {
            restClient.delete().uri("/{index}", properties.indexName()).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // 首次部署没有旧索引是正常状态。
        }
    }

    /** 以“类型:业务主键”作为稳定文档 ID，重复消费不会产生重复数据。 */
    @Retry(name = "openSearchWrite")
    @CircuitBreaker(name = "openSearch")
    @Bulkhead(name = "openSearch", type = Bulkhead.Type.SEMAPHORE)
    public void upsert(SearchSourceDocument source, List<Double> embedding) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", source.id());
        body.put("documentType", source.documentType());
        body.put("title", source.title());
        body.put("content", source.content());
        body.put("authorName", source.authorName());
        body.put("avatarUrl", source.avatarUrl());
        body.put("sourceUrl", source.sourceUrl());
        body.put("routePath", source.routePath());
        body.put("petType", source.petType());
        body.put("category", source.category());
        body.put("trustLevel", source.trustLevel());
        body.put("publishedAt", source.publishedAt());
        body.put("embedding", embedding);
        restClient.put()
                .uri("/{index}/_doc/{id}", properties.indexName(), documentId(source.documentType(), source.id()))
                .body(body).retrieve().toBodilessEntity();
    }

    /** 内容撤回、删除或失去公开资格时立即移除搜索副本。 */
    @Retry(name = "openSearchWrite")
    @CircuitBreaker(name = "openSearch")
    @Bulkhead(name = "openSearch", type = Bulkhead.Type.SEMAPHORE)
    public void delete(String type, String id) {
        try {
            restClient.delete().uri("/{index}/_doc/{id}", properties.indexName(), documentId(type, id))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // 幂等删除：索引中已经不存在即达到目标状态。
        }
    }

    /** BM25 为主、BGE 向量为辅，使用倒数排名融合得到稳定的混合排序。 */
    @Retry(name = "openSearchRead")
    @CircuitBreaker(name = "openSearch")
    @Bulkhead(name = "openSearch", type = Bulkhead.Type.SEMAPHORE)
    public OpenSearchPage search(
            String query,
            String type,
            String petType,
            String category,
            String trustLevel,
            Instant publishedAfter,
            String sort,
            int page,
            int size,
            List<Double> embedding
    ) {
        boolean hybrid = "RELEVANCE".equals(sort) && embedding != null && !embedding.isEmpty();
        OpenSearchPage keywordPage = keywordSearch(
                query, type, petType, category, trustLevel, publishedAfter, sort,
                hybrid ? 0 : page, hybrid ? Math.max(40, (page + 1) * size) : size
        );
        if (!hybrid) return keywordPage;

        OpenSearchPage vectorPage = vectorSearch(
                type, petType, category, trustLevel, publishedAfter, Math.max(40, (page + 1) * size), embedding
        );
        Map<String, SearchSourceDocument> sources = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        Map<String, Set<String>> matched = new HashMap<>();
        fuse(keywordPage.items(), 0.65D, sources, scores, matched);
        fuse(vectorPage.items(), 0.35D, sources, scores, matched);
        List<OpenSearchHit> merged = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .skip((long) page * size).limit(size)
                .map(entry -> new OpenSearchHit(
                        sources.get(entry.getKey()),
                        entry.getValue(),
                        List.copyOf(matched.getOrDefault(entry.getKey(), Set.of()))
                ))
                .toList();
        return new OpenSearchPage(Math.max(keywordPage.total(), merged.size()), merged);
    }

    private OpenSearchPage keywordSearch(
            String query, String type, String petType, String category, String trustLevel,
            Instant publishedAfter, String sort, int page, int size
    ) {
        List<Map<String, Object>> filters = filters(type, petType, category, trustLevel, publishedAfter);
        Map<String, Object> multiMatch = Map.of(
                "query", query,
                "fields", List.of("title^4", "authorName^2", "content"),
                "type", "best_fields"
        );
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of("multi_match", multiMatch)));
        if (!filters.isEmpty()) bool.put("filter", filters);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", page * size);
        body.put("size", size);
        body.put("track_total_hits", true);
        body.put("query", Map.of("bool", bool));
        body.put("highlight", Map.of("fields", Map.of(
                "title", Map.of(), "content", Map.of(), "authorName", Map.of()
        )));
        if ("LATEST".equals(sort)) body.put("sort", List.of(Map.of("publishedAt", "desc")));
        return executeSearch(body);
    }

    private OpenSearchPage vectorSearch(
            String type, String petType, String category, String trustLevel,
            Instant publishedAfter, int k, List<Double> embedding
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", k);
        body.put("track_total_hits", true);
        List<Map<String, Object>> filters = filters(type, petType, category, trustLevel, publishedAfter);
        Map<String, Object> vectorQuery = new LinkedHashMap<>();
        vectorQuery.put("vector", embedding);
        vectorQuery.put("k", k);
        // Lucene k-NN 支持查询内高效过滤；不能使用 post_filter 后置裁剪，否则可能少于 k 条。
        if (!filters.isEmpty()) vectorQuery.put("filter", Map.of("bool", Map.of("filter", filters)));
        body.put("query", Map.of("knn", Map.of("embedding", vectorQuery)));
        return executeSearch(body);
    }

    private OpenSearchPage executeSearch(Map<String, Object> body) {
        JsonNode root = restClient.post().uri("/{index}/_search", properties.indexName())
                .body(body).retrieve().body(JsonNode.class);
        if (root == null) return new OpenSearchPage(0, List.of());
        long total = root.path("hits").path("total").path("value").asLong(0);
        List<OpenSearchHit> items = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            SearchSourceDocument source = objectMapper.convertValue(hit.path("_source"), SearchSourceDocument.class);
            List<String> matchedFields = new ArrayList<>();
            hit.path("highlight").fieldNames().forEachRemaining(matchedFields::add);
            items.add(new OpenSearchHit(source, hit.path("_score").asDouble(0), matchedFields));
        }
        return new OpenSearchPage(total, items);
    }

    private List<Map<String, Object>> filters(
            String type, String petType, String category, String trustLevel, Instant publishedAfter
    ) {
        List<Map<String, Object>> filters = new ArrayList<>();
        addTerm(filters, "documentType", "ALL".equals(type) ? null : type);
        addTerm(filters, "petType", petType);
        addTerm(filters, "category", category);
        addTerm(filters, "trustLevel", trustLevel);
        if (publishedAfter != null) {
            filters.add(Map.of("range", Map.of("publishedAt", Map.of("gte", publishedAfter.toString()))));
        }
        return filters;
    }

    private void addTerm(List<Map<String, Object>> filters, String field, String value) {
        if (value != null && !value.isBlank()) filters.add(Map.of("term", Map.of(field, value)));
    }

    private void fuse(
            List<OpenSearchHit> hits,
            double weight,
            Map<String, SearchSourceDocument> sources,
            Map<String, Double> scores,
            Map<String, Set<String>> matched
    ) {
        for (int rank = 0; rank < hits.size(); rank++) {
            OpenSearchHit hit = hits.get(rank);
            String key = documentId(hit.source().documentType(), hit.source().id());
            sources.put(key, hit.source());
            scores.merge(key, weight / (60D + rank + 1D), Double::sum);
            matched.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(hit.matchedFields());
        }
    }

    private String documentId(String type, String id) {
        return Objects.requireNonNull(type).toLowerCase() + ":" + Objects.requireNonNull(id);
    }
}
