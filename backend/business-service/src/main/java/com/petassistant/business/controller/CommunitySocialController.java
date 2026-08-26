package com.petassistant.business.controller;

import java.security.Principal;
import java.time.LocalDate;

import com.petassistant.business.data.dto.request.CreateCommunityCommentRequest;
import com.petassistant.business.data.dto.request.CreateCommunityReportRequest;
import com.petassistant.business.data.dto.response.CommunityCheckInResponse;
import com.petassistant.business.data.dto.response.CommunityCommentPageResponse;
import com.petassistant.business.data.dto.response.CommunityCommentResponse;
import com.petassistant.business.data.dto.response.CommunityFollowResponse;
import com.petassistant.business.data.dto.response.CommunityReactionResponse;
import com.petassistant.business.data.dto.response.CommunityReportResponse;
import com.petassistant.business.service.CommunitySocialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 普通用户的社区互动入口。 */
@RestController
@RequestMapping("/api/v1/community")
public class CommunitySocialController {

    private final CommunitySocialService service;

    public CommunitySocialController(CommunitySocialService service) {
        this.service = service;
    }

    /**
     * 发表评论或回复评论
     * @param principal
     * @param postId
     * @param request
     * @return
     */
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityCommentResponse createComment(
            Principal principal,
            @PathVariable String postId,
            @Valid @RequestBody CreateCommunityCommentRequest request
    ) {
        return service.createComment(principal.getName(), postId, request);
    }

    /**
     * 获取评论列表
     * @param principal
     * @param postId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/posts/{postId}/comments")
    public CommunityCommentPageResponse comments(
            Principal principal,
            @PathVariable String postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.comments(principal.getName(), postId, page, size);
    }

    /**
     * 删除评论
     * @param principal
     * @param commentId
     */

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(Principal principal, @PathVariable String commentId) {
        service.deleteComment(principal.getName(), commentId);
    }

    /**
     * 点赞帖子
     * @param principal
     * @param postId
     * @return
     */

    @PutMapping("/posts/{postId}/like")
    public CommunityReactionResponse like(Principal principal, @PathVariable String postId) {
        return service.like(principal.getName(), postId, true);
    }

    /**
     * 取消点赞帖子
     * @param principal
     * @param postId
     * @return
     */
    @DeleteMapping("/posts/{postId}/like")
    public CommunityReactionResponse unlike(Principal principal, @PathVariable String postId) {
        return service.like(principal.getName(), postId, false);
    }

    /**
     * 收藏帖子
     * @param principal
     * @param postId
     * @return
     */
    @PutMapping("/posts/{postId}/favorite")
    public CommunityReactionResponse favorite(Principal principal, @PathVariable String postId) {
        return service.favorite(principal.getName(), postId, true);
    }

    /**
     * 取消收藏帖子
     * @param principal
     * @param postId
     * @return
     */
    @DeleteMapping("/posts/{postId}/favorite")
    public CommunityReactionResponse unfavorite(Principal principal, @PathVariable String postId) {
        return service.favorite(principal.getName(), postId, false);
    }

    /**
     * 关注用户
     * @param principal
     * @param userId
     * @return
     */

    @PutMapping("/users/{userId}/follow")
    public CommunityFollowResponse follow(Principal principal, @PathVariable String userId) {
        return service.follow(principal.getName(), userId, true);
    }

    /**
     * 取关用户
     * @param principal
     * @param userId
     * @return
     */
    @DeleteMapping("/users/{userId}/follow")
    public CommunityFollowResponse unfollow(Principal principal, @PathVariable String userId) {
        return service.follow(principal.getName(), userId, false);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityReportResponse report(
            Principal principal,
            @Valid @RequestBody CreateCommunityReportRequest request
    ) {
        return service.report(principal.getName(), request);
    }

    @PutMapping("/check-ins/today")
    public CommunityCheckInResponse checkIn(Principal principal) {
        return service.checkIn(principal.getName(), LocalDate.now());
    }

    @GetMapping("/check-ins/today")
    public CommunityCheckInResponse checkInStatus(Principal principal) {
        return service.checkInStatus(principal.getName(), LocalDate.now());
    }
}
