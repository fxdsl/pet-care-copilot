package com.petassistant.business.controller;

import java.security.Principal;
import java.util.List;

import com.petassistant.business.data.dto.request.CreateCommunityPostRequest;
import com.petassistant.business.data.dto.request.UpdateCommunityPostRequest;
import com.petassistant.business.data.dto.response.CommunityPostPageResponse;
import com.petassistant.business.data.dto.response.CommunityPostResponse;
import com.petassistant.business.data.dto.response.CommunityTopicResponse;
import com.petassistant.business.service.CommunityPostService;
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

/** 普通养宠用户的社区帖子入口。 */
@RestController
@RequestMapping("/api/v1/community")
public class CommunityPostController {

    private final CommunityPostService service;

    public CommunityPostController(CommunityPostService service) { this.service = service; }

    @GetMapping("/topics")
    public List<CommunityTopicResponse> topics() { return service.topics(); }

    /**
     * 创建帖子草稿
     * @param principal
     * @param request
     * @return
     */
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityPostResponse create(
            Principal principal, @Valid @RequestBody CreateCommunityPostRequest request
    ) { return service.create(principal.getName(), request); }

    /**
     * 更新帖子草稿
     * @param principal
     * @param postId
     * @param request
     * @return
     */
    @PutMapping("/posts/{postId}")
    public CommunityPostResponse update(
            Principal principal,
            @PathVariable String postId,
            @Valid @RequestBody UpdateCommunityPostRequest request
    ) { return service.update(principal.getName(), postId, request); }

    /**
     * 发布帖子
     * @param principal
     * @param postId
     * @return
     */
    @PostMapping("/posts/{postId}/publish")
    public CommunityPostResponse publish(Principal principal, @PathVariable String postId) {
        return service.publish(principal.getName(), postId);
    }

    /**
     * 删除帖子草稿
     * @param principal
     * @param postId
     */
    @DeleteMapping("/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable String postId) {
        service.delete(principal.getName(), postId);
    }

    /**
     * 获取帖子详情
     * @param postId
     * @return
     */
    @GetMapping("/posts/{postId}")
    public CommunityPostResponse detail(Principal principal, @PathVariable String postId) {
        return service.publicDetail(principal.getName(), postId); }

    /**
     * 获取帖子列表
     * @param topicId
     * @param authorId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/posts")
    public CommunityPostPageResponse posts(
            Principal principal,
            @RequestParam(defaultValue = "LATEST") String feed,
            @RequestParam(required = false) String topicId,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "20") double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.publicFeed(
                principal.getName(), feed, topicId, authorId, latitude, longitude, radiusKm, page, size
        );
    }

    /**
     * 获取用户自己的帖子列表
     * @param principal
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/posts/mine")
    public CommunityPostPageResponse mine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) { return service.mine(principal.getName(), page, size); }
}
