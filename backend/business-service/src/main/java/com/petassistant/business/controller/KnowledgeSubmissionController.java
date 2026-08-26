package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.CreateCommunityKnowledgeSubmissionRequest;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionPageResponse;
import com.petassistant.business.data.dto.response.KnowledgeSubmissionResponse;
import com.petassistant.business.service.KnowledgeSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 普通用户知识共建接口：社区经验投稿、查看进度和撤回授权。 */
@RestController
@RequestMapping("/api/v1/knowledge-submissions")
public class KnowledgeSubmissionController {

    private final KnowledgeSubmissionService service;

    public KnowledgeSubmissionController(KnowledgeSubmissionService service) {
        this.service = service;
    }

    /** 创建社区帖子知识投稿，返回 PRECHECKING 状态。 */
    @PostMapping("/community")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeSubmissionResponse submitCommunity(
            Principal principal,
            @Valid @RequestBody CreateCommunityKnowledgeSubmissionRequest request
    ) {
        return service.submitCommunity(principal.getName(), request);
    }

    /** 分页查询当前用户的投稿状态。 */
    @GetMapping("/mine")
    public KnowledgeSubmissionPageResponse mine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.mine(principal.getName(), page, size);
    }

    /** 查询自己的投稿正文、预检结果和审核时间线。 */
    @GetMapping("/{submissionId}")
    public KnowledgeSubmissionResponse detail(Principal principal, @PathVariable String submissionId) {
        return service.ownedDetail(principal.getName(), submissionId);
    }

    /** 撤回内容授权并立即让已经发布的文档退出 RAG。 */
    @DeleteMapping("/{submissionId}")
    public KnowledgeSubmissionResponse withdraw(Principal principal, @PathVariable String submissionId) {
        return service.withdraw(principal.getName(), submissionId);
    }
}
