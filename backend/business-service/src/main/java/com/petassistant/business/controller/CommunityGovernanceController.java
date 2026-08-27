package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.CreateCommunityRepostRequest;
import com.petassistant.business.data.dto.response.CommunityRelationControlResponse;
import com.petassistant.business.data.dto.response.CommunityRepostResponse;
import com.petassistant.business.data.dto.response.RecommendationFeedbackResponse;
import com.petassistant.business.data.dto.response.RecommendationPageResponse;
import com.petassistant.business.service.CommunityGovernanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 第十三周转发、关系治理和推荐反馈接口。 */
@RestController
@RequestMapping("/api/v1/community")
public class CommunityGovernanceController {

    private final CommunityGovernanceService service;

    public CommunityGovernanceController(CommunityGovernanceService service) { this.service = service; }

    @PutMapping("/posts/{postId}/repost")
    public CommunityRepostResponse repost(
            Principal principal, @PathVariable String postId,
            @Valid @RequestBody(required = false) CreateCommunityRepostRequest request
    ) { return service.repost(principal.getName(), postId, true, request); }

    @DeleteMapping("/posts/{postId}/repost")
    public CommunityRepostResponse removeRepost(Principal principal, @PathVariable String postId) {
        return service.repost(principal.getName(), postId, false, null);
    }

    @PutMapping("/users/{userId}/{relationType}")
    public CommunityRelationControlResponse enableRelation(
            Principal principal, @PathVariable String userId, @PathVariable String relationType
    ) { return service.relation(principal.getName(), userId, relationType, true); }

    @DeleteMapping("/users/{userId}/{relationType}")
    public CommunityRelationControlResponse disableRelation(
            Principal principal, @PathVariable String userId, @PathVariable String relationType
    ) { return service.relation(principal.getName(), userId, relationType, false); }

    @GetMapping("/recommendations")
    public RecommendationPageResponse recommendations(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) { return service.recommendations(principal.getName(), page, size); }

    @PutMapping("/recommendations/{postId}/not-interested")
    public RecommendationFeedbackResponse notInterested(Principal principal, @PathVariable String postId) {
        return service.notInterested(principal.getName(), postId, true);
    }

    @DeleteMapping("/recommendations/{postId}/not-interested")
    public RecommendationFeedbackResponse undoNotInterested(Principal principal, @PathVariable String postId) {
        return service.notInterested(principal.getName(), postId, false);
    }
}
