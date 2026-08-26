package com.petassistant.business.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.KnowledgeAiClient;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.config.SearchProperties;
import com.petassistant.business.data.dto.internal.OpenSearchHit;
import com.petassistant.business.data.dto.internal.OpenSearchPage;
import com.petassistant.business.data.dto.internal.SearchSourceDocument;
import com.petassistant.business.data.dto.response.SearchGroupResponse;
import com.petassistant.business.data.dto.response.SearchHistoryResponse;
import com.petassistant.business.data.dto.response.SearchResultItemResponse;
import com.petassistant.business.data.dto.response.SearchSuggestionResponse;
import com.petassistant.business.data.dto.response.SearchTrendingResponse;
import com.petassistant.business.data.dto.response.UnifiedSearchResponse;
import com.petassistant.business.data.entity.SearchHistoryEntity;
import com.petassistant.business.data.mapper.SearchMapper;
import com.petassistant.business.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一搜索编排：缓存 → OpenSearch 混合检索 → MySQL 权限等价降级。 */
@Service
public class UnifiedSearchService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedSearchService.class);
    private static final List<String> TYPES = List.of("POST", "KNOWLEDGE", "USER", "TOPIC");
    private static final Set<String> SORTS = Set.of("RELEVANCE", "LATEST");
    private static final Set<String> DATE_RANGES = Set.of("ALL", "LAST_7_DAYS", "LAST_30_DAYS", "LAST_YEAR");

    private final SearchMapper mapper;
    private final SearchIndexClient indexClient;
    private final KnowledgeAiClient aiClient;
    private final SearchCacheService cacheService;
    private final SearchProperties properties;
    private final ObjectMapper objectMapper;

    public UnifiedSearchService(
            SearchMapper mapper,
            SearchIndexClient indexClient,
            KnowledgeAiClient aiClient,
            SearchCacheService cacheService,
            SearchProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.indexClient = indexClient;
        this.aiClient = aiClient;
        this.cacheService = cacheService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 执行公开搜索，并把当前用户的查询历史独立写入 MySQL。 */
    public UnifiedSearchResponse search(
            String userId,
            String query,
            String type,
            String petType,
            String category,
            String trustLevel,
            String dateRange,
            String sort,
            int page,
            int size
    ) {
        SearchCriteria criteria = validate(query, type, petType, category, trustLevel, dateRange, sort, page, size);
        long version = cacheService.indexVersion();
        String cacheKey = "search:result:" + version + ":" + sha256(criteria.cacheIdentity());
        UnifiedSearchResponse cached = cacheService.getResult(cacheKey);
        if (cached != null) {
            recordHistory(userId, criteria, cached.total());
            cacheService.recordTrending(criteria.normalizedQuery());
            return cached;
        }

        UnifiedSearchResponse response;
        try {
            if (!properties.enabled()) throw new IllegalStateException("OpenSearch is disabled");
            List<Double> vector = List.of();
            if ("RELEVANCE".equals(criteria.sort())) {
                try {
                    vector = aiClient.embedForSearch(criteria.normalizedQuery(), false).embedding();
                } catch (RuntimeException error) {
                    log.info("Search vector unavailable, continuing with BM25: {}", error.toString());
                }
            }
            response = searchOpenSearch(criteria, vector, version);
        } catch (RuntimeException error) {
            log.warn("OpenSearch query failed, using permission-equivalent MySQL fallback: {}", error.toString());
            response = searchMySql(criteria, version);
        }
        cacheService.putResult(cacheKey, response);
        recordHistory(userId, criteria, response.total());
        cacheService.recordTrending(criteria.normalizedQuery());
        return response;
    }

    /** 联想按“个人历史、趋势、公开 MySQL 标题”依次去重，避免公开私人原始查询。 */
    public List<SearchSuggestionResponse> suggestions(String userId, String query, int limit) {
        if (!cacheService.allowSuggestion(userId, 30)) {
            throw new TooManyRequestsException("搜索联想过于频繁，请稍后再试");
        }
        String normalized = normalizeQuery(query);
        int safeLimit = Math.max(1, Math.min(limit, 10));
        Map<String, SearchSuggestionResponse> unique = new LinkedHashMap<>();
        for (SearchHistoryEntity history : mapper.findHistory(userId, 20)) {
            if (history.normalizedQuery().startsWith(normalized)) {
                unique.putIfAbsent(history.queryText(), new SearchSuggestionResponse(history.queryText(), "HISTORY"));
            }
        }
        for (SearchTrendingResponse trend : cacheService.trending(20)) {
            if (trend.query().startsWith(normalized)) {
                unique.putIfAbsent(trend.query(), new SearchSuggestionResponse(trend.query(), "TRENDING"));
            }
        }
        List<String> publicSuggestions = cacheService.getPublicSuggestions(normalized);
        if (publicSuggestions == null) {
            publicSuggestions = mapper.findSuggestions(normalized, safeLimit);
            cacheService.putPublicSuggestions(normalized, publicSuggestions);
        }
        for (String candidate : publicSuggestions) {
            unique.putIfAbsent(candidate, new SearchSuggestionResponse(candidate, "PUBLIC_CONTENT"));
        }
        return unique.values().stream().limit(safeLimit).toList();
    }

    public List<SearchHistoryResponse> history(String userId, int limit) {
        return mapper.findHistory(userId, Math.max(1, Math.min(limit, 50))).stream()
                .map(item -> new SearchHistoryResponse(
                        item.id(), item.queryText(), item.filtersJson(), item.resultCount(),
                        item.searchCount(), item.lastSearchedAt()
                )).toList();
    }

    @Transactional
    public void deleteHistory(String userId, String id) {
        mapper.deleteHistory(id, userId);
    }

    @Transactional
    public void clearHistory(String userId) {
        mapper.deleteAllHistory(userId);
    }

    public List<SearchTrendingResponse> trending(int limit) {
        return cacheService.trending(Math.max(1, Math.min(limit, 20)));
    }

    private UnifiedSearchResponse searchOpenSearch(SearchCriteria criteria, List<Double> vector, long version) {
        List<SearchGroupResponse> groups = new ArrayList<>();
        long total = 0;
        for (String groupType : criteria.resolvedTypes()) {
            OpenSearchPage result = indexClient.search(
                    criteria.normalizedQuery(), groupType, criteria.petType(), criteria.category(),
                    criteria.trustLevel(), criteria.publishedAfter(), criteria.sort(),
                    criteria.page(), criteria.size(), vector
            );
            total += result.total();
            groups.add(new SearchGroupResponse(
                    groupType, result.total(), result.items().stream().map(this::toResponse).toList()
            ));
        }
        return new UnifiedSearchResponse(
                criteria.query(), criteria.type(), criteria.page(), criteria.size(), total,
                "OPENSEARCH", false, version, groups
        );
    }

    private UnifiedSearchResponse searchMySql(SearchCriteria criteria, long version) {
        List<SearchGroupResponse> groups = new ArrayList<>();
        long total = 0;
        for (String groupType : criteria.resolvedTypes()) {
            List<SearchSourceDocument> documents = mapper.findFallback(
                    criteria.normalizedQuery(), groupType, criteria.petType(), criteria.category(),
                    criteria.trustLevel(), criteria.publishedAfter(), criteria.sort(),
                    criteria.page() * criteria.size(), criteria.size()
            );
            long groupTotal = mapper.countFallback(
                    criteria.normalizedQuery(), groupType, criteria.petType(), criteria.category(),
                    criteria.trustLevel(), criteria.publishedAfter()
            );
            total += groupTotal;
            groups.add(new SearchGroupResponse(
                    groupType, groupTotal,
                    documents.stream().map(source -> toResponse(source, criteria.normalizedQuery())).toList()
            ));
        }
        return new UnifiedSearchResponse(
                criteria.query(), criteria.type(), criteria.page(), criteria.size(), total,
                "MYSQL", true, version, groups
        );
    }

    private SearchResultItemResponse toResponse(OpenSearchHit hit) {
        SearchSourceDocument source = hit.source();
        return new SearchResultItemResponse(
                source.id(), source.documentType(), source.title(), snippet(source.content()),
                source.authorName(), source.avatarUrl(), source.sourceUrl(), source.routePath(),
                source.petType(), source.category(), source.trustLevel(), source.publishedAt(),
                hit.score(), hit.matchedFields()
        );
    }

    private SearchResultItemResponse toResponse(SearchSourceDocument source, String query) {
        List<String> matched = new ArrayList<>();
        if (contains(source.title(), query)) matched.add("title");
        if (contains(source.content(), query)) matched.add("content");
        if (contains(source.authorName(), query)) matched.add("authorName");
        return new SearchResultItemResponse(
                source.id(), source.documentType(), source.title(), snippet(source.content()),
                source.authorName(), source.avatarUrl(), source.sourceUrl(), source.routePath(),
                source.petType(), source.category(), source.trustLevel(), source.publishedAt(),
                0D, matched
        );
    }

    private void recordHistory(String userId, SearchCriteria criteria, long resultCount) {
        Instant now = Instant.now();
        String filtersJson;
        try {
            filtersJson = objectMapper.writeValueAsString(Map.of(
                    "type", criteria.type(), "petType", nullToEmpty(criteria.petType()),
                    "category", nullToEmpty(criteria.category()), "trustLevel", nullToEmpty(criteria.trustLevel()),
                    "dateRange", criteria.dateRange(), "sort", criteria.sort()
            ));
        } catch (JsonProcessingException error) {
            filtersJson = "{}";
        }
        mapper.upsertHistory(new SearchHistoryEntity(
                UUID.randomUUID().toString(), userId, criteria.query(), criteria.normalizedQuery(),
                sha256(criteria.normalizedQuery() + "|" + filtersJson), filtersJson, resultCount,
                1, now, now, now
        ));
    }

    private SearchCriteria validate(
            String query, String type, String petType, String category, String trustLevel,
            String dateRange, String sort, int page, int size
    ) {
        String normalized = normalizeQuery(query);
        String resolvedType = upper(type, "ALL");
        if (!"ALL".equals(resolvedType) && !TYPES.contains(resolvedType)) throw new IllegalArgumentException("不支持的搜索类型");
        String resolvedSort = upper(sort, "RELEVANCE");
        if (!SORTS.contains(resolvedSort)) throw new IllegalArgumentException("不支持的排序方式");
        String resolvedDateRange = upper(dateRange, "ALL");
        if (!DATE_RANGES.contains(resolvedDateRange)) throw new IllegalArgumentException("不支持的时间范围");
        if (page < 0) throw new IllegalArgumentException("页码不能小于 0");
        if (size < 1 || size > 20) throw new IllegalArgumentException("每页数量必须在 1 到 20 之间");
        Instant publishedAfter = switch (resolvedDateRange) {
            case "LAST_7_DAYS" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "LAST_30_DAYS" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "LAST_YEAR" -> Instant.now().minus(365, ChronoUnit.DAYS);
            default -> null;
        };
        return new SearchCriteria(
                query.trim(), normalized, resolvedType, blankToNull(petType), blankToNull(category),
                blankToNull(trustLevel), resolvedDateRange, resolvedSort, publishedAfter, page, size
        );
    }

    private String normalizeQuery(String query) {
        if (query == null) throw new IllegalArgumentException("请输入搜索关键词");
        String normalized = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 120) throw new IllegalArgumentException("搜索关键词长度必须在 1 到 120 个字符之间");
        return normalized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM 缺少 SHA-256", error);
        }
    }

    private String snippet(String content) {
        if (content == null) return "";
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "…";
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String upper(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SearchCriteria(
            String query, String normalizedQuery, String type, String petType, String category,
            String trustLevel, String dateRange, String sort, Instant publishedAfter, int page, int size
    ) {
        List<String> resolvedTypes() {
            return "ALL".equals(type) ? TYPES : List.of(type);
        }

        String cacheIdentity() {
            return String.join("|", normalizedQuery, type, String.valueOf(petType), String.valueOf(category),
                    String.valueOf(trustLevel), dateRange, sort, String.valueOf(page), String.valueOf(size));
        }
    }
}
