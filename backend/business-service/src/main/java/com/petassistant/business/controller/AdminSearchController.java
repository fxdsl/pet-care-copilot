package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.response.SearchIndexJobResponse;
import com.petassistant.business.service.SearchIndexService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 管理员发起并查看 OpenSearch 全量重建任务。 */
@RestController
@RequestMapping("/api/v1/admin/search")
public class AdminSearchController {

    private final SearchIndexService service;

    public AdminSearchController(SearchIndexService service) {
        this.service = service;
    }

    /** 创建异步全量重建任务并返回 202。 */
    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SearchIndexJobResponse rebuild(Principal principal) {
        return service.requestRebuild(principal.getName());
    }

    /** 读取指定重建任务的 MySQL 事实进度。 */
    @GetMapping("/rebuild/{jobId}")
    public SearchIndexJobResponse job(@PathVariable String jobId) {
        return service.job(jobId);
    }
}
