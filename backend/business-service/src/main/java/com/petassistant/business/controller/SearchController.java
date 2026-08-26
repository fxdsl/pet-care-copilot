package com.petassistant.business.controller;

import java.security.Principal;
import java.util.List;

import com.petassistant.business.data.dto.response.SearchHistoryResponse;
import com.petassistant.business.data.dto.response.SearchSuggestionResponse;
import com.petassistant.business.data.dto.response.SearchTrendingResponse;
import com.petassistant.business.data.dto.response.UnifiedSearchResponse;
import com.petassistant.business.service.UnifiedSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的统一搜索、联想、趋势与私人历史接口。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final UnifiedSearchService service;

    public SearchController(UnifiedSearchService service) {
        this.service = service;
    }

    /** 按 URL Query 执行四类公开内容搜索。 */
    @GetMapping
    public UnifiedSearchResponse search(
            Principal principal,
            @RequestParam String query,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String trustLevel,
            @RequestParam(defaultValue = "ALL") String dateRange,
            @RequestParam(defaultValue = "RELEVANCE") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.search(
                principal.getName(), query, type, petType, category, trustLevel, dateRange, sort, page, size
        );
    }

    /** 返回当前用户历史、脱敏趋势和公开内容标题组成的联想词。 */
    @GetMapping("/suggestions")
    public List<SearchSuggestionResponse> suggestions(
            Principal principal,
            @RequestParam String query,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return service.suggestions(principal.getName(), query, limit);
    }

    /** 查询当前登录用户自己的最近搜索历史。 */
    @GetMapping("/history")
    public List<SearchHistoryResponse> history(
            Principal principal, @RequestParam(defaultValue = "20") int limit
    ) {
        return service.history(principal.getName(), limit);
    }

    /** 幂等删除当前用户拥有的指定历史记录。 */
    @DeleteMapping("/history/{historyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(Principal principal, @PathVariable String historyId) {
        service.deleteHistory(principal.getName(), historyId);
    }

    /** 清空当前用户自己的全部搜索历史。 */
    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(Principal principal) {
        service.clearHistory(principal.getName());
    }

    /** 返回已经过敏感查询过滤的 Redis 热词。 */
    @GetMapping("/trending")
    public List<SearchTrendingResponse> trending(@RequestParam(defaultValue = "10") int limit) {
        return service.trending(limit);
    }
}
