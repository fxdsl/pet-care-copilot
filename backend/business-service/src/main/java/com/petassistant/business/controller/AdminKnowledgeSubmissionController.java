package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.CreateAdminKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.request.ReviewKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionPageResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionStatsResponse;
import com.petassistant.business.service.KnowledgeSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 管理员知识审核工作台接口。 */
@RestController
@RequestMapping("/api/v1/admin/knowledge-submissions")
public class AdminKnowledgeSubmissionController {

    private final KnowledgeSubmissionService service;

    public AdminKnowledgeSubmissionController(KnowledgeSubmissionService service) {
        this.service = service;
    }

    /** 管理员登记可信资料，仍需经过预检与人工批准。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeSubmissionResponse upload(
            Principal principal,
            @Valid @RequestBody CreateAdminKnowledgeSubmissionRequest request
    ) {
        return service.submitAdminUpload(principal.getName(), request);
    }

    /** 按状态、风险和来源类型筛选审核队列。 */
    @GetMapping
    public KnowledgeSubmissionPageResponse page(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.reviewPage(status, riskLevel, sourceType, page, size);
    }

    /** 查询工作台统计卡片。 */
    @GetMapping("/stats")
    public KnowledgeSubmissionStatsResponse stats() {
        return service.stats();
    }

    /** 查询原文、清洗对比和完整审核时间线。 */
    @GetMapping("/{submissionId}")
    public KnowledgeSubmissionResponse detail(@PathVariable String submissionId) {
        return service.adminDetail(submissionId);
    }

    /** 人工批准或驳回；批准后由 RabbitMQ 异步向量化发布。 */
    @PostMapping("/{submissionId}/review")
    public KnowledgeSubmissionResponse review(
            Principal principal,
            @PathVariable String submissionId,
            @Valid @RequestBody ReviewKnowledgeSubmissionRequest request
    ) {
        return service.review(principal.getName(), submissionId, request);
    }
}
