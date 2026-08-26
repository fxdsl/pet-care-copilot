package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.response.CommunityPostPageResponse;
import com.petassistant.business.data.dto.response.PublicUserPageResponse;
import com.petassistant.business.data.dto.response.PublicUserProfileResponse;
import com.petassistant.business.service.CommunityProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 社区公开主页、关系列表和当前用户私有收藏入口。 */
@RestController
@RequestMapping("/api/v1/community")
public class CommunityProfileController {

    private final CommunityProfileService service;

    public CommunityProfileController(CommunityProfileService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/profile")
    public PublicUserProfileResponse profile(Principal principal, @PathVariable String userId) {
        return service.profile(principal.getName(), userId);
    }

    @GetMapping("/users/{userId}/followers")
    public PublicUserPageResponse followers(
            Principal principal, @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return service.followers(principal.getName(), userId, page, size);
    }

    @GetMapping("/users/{userId}/following")
    public PublicUserPageResponse following(
            Principal principal, @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return service.following(principal.getName(), userId, page, size);
    }

    @GetMapping("/users/me/liked-posts")
    public CommunityPostPageResponse liked(
            Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return service.liked(principal.getName(), page, size);
    }

    @GetMapping("/users/me/favorite-posts")
    public CommunityPostPageResponse favorited(
            Principal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size
    ) {
        return service.favorited(principal.getName(), page, size);
    }
}
