package com.petassistant.business.service;

import java.util.List;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.internal.PublicUserSummaryView;
import com.petassistant.business.data.dto.response.CommunityPostPageResponse;
import com.petassistant.business.data.dto.response.CommunityPostResponse;
import com.petassistant.business.data.dto.response.PublicUserPageResponse;
import com.petassistant.business.data.dto.response.PublicUserProfileResponse;
import com.petassistant.business.data.dto.response.PublicUserSummaryResponse;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.CommunitySocialMapper;
import com.petassistant.business.data.mapper.PetProfileMapper;
import com.petassistant.business.data.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公开个人主页聚合服务；所有公开边界在后端固定，不复用“我的资料”响应。 */
@Service
public class CommunityProfileService {

    private final UserMapper userMapper;
    private final PetProfileMapper petProfileMapper;
    private final CommunityPostMapper postMapper;
    private final CommunitySocialMapper socialMapper;
    private final CommunityPostService postService;
    private final CommunitySocialService socialService;

    public CommunityProfileService(
            UserMapper userMapper,
            PetProfileMapper petProfileMapper,
            CommunityPostMapper postMapper,
            CommunitySocialMapper socialMapper,
            CommunityPostService postService,
            CommunitySocialService socialService
    ) {
        this.userMapper = userMapper;
        this.petProfileMapper = petProfileMapper;
        this.postMapper = postMapper;
        this.socialMapper = socialMapper;
        this.postService = postService;
        this.socialService = socialService;
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponse profile(String viewerId, String userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.status())) throw new IllegalArgumentException("用户不存在或不可用");
        List<PublicUserProfileResponse.PetSummary> pets = petProfileMapper.findRecentByUser(userId, 6)
                .stream().map(pet -> new PublicUserProfileResponse.PetSummary(
                        pet.id(), pet.name(), pet.petType(), pet.breed(), pet.ageMonths()
                )).toList();
        return new PublicUserProfileResponse(
                user.id(), user.username(), user.displayName(), user.avatarUrl(), user.bio(), user.region(),
                user.createdAt(), postMapper.countPublic(null, userId), socialMapper.countFollowers(userId),
                socialMapper.countFollowing(userId), socialMapper.existsFollow(viewerId, userId),
                viewerId.equals(userId), pets
        );
    }

    @Transactional(readOnly = true)
    public PublicUserPageResponse followers(String viewerId, String userId, int page, int size) {
        profile(viewerId, userId);
        return users(socialMapper.findFollowers(userId, viewerId, offset(page, size), limit(size)),
                page, size, socialMapper.countFollowers(userId));
    }

    @Transactional(readOnly = true)
    public PublicUserPageResponse following(String viewerId, String userId, int page, int size) {
        profile(viewerId, userId);
        return users(socialMapper.findFollowing(userId, viewerId, offset(page, size), limit(size)),
                page, size, socialMapper.countFollowing(userId));
    }

    @Transactional(readOnly = true)
    public CommunityPostPageResponse liked(String userId, int page, int size) {
        return relatedPosts(userId, true, page, size);
    }

    @Transactional(readOnly = true)
    public CommunityPostPageResponse favorited(String userId, int page, int size) {
        return relatedPosts(userId, false, page, size);
    }

    private CommunityPostPageResponse relatedPosts(String userId, boolean liked, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = limit(size);
        List<CommunityPostView> rows = liked
                ? postMapper.findLikedByUser(userId, safePage * safeSize, safeSize)
                : postMapper.findFavoritedByUser(userId, safePage * safeSize, safeSize);
        List<CommunityPostResponse> posts = rows.stream().map(postService::toResponse).toList();
        long total = liked ? postMapper.countLikedByUser(userId) : postMapper.countFavoritedByUser(userId);
        return new CommunityPostPageResponse(socialService.decoratePosts(userId, posts), safePage, safeSize, total);
    }

    private static PublicUserPageResponse users(
            List<PublicUserSummaryView> rows, int page, int size, long total
    ) {
        return new PublicUserPageResponse(
                rows.stream().map(row -> new PublicUserSummaryResponse(
                        row.id(), row.username(), row.displayName(), row.avatarUrl(), row.bio(), row.region(),
                        row.viewerFollowing()
                )).toList(), Math.max(0, page), limit(size), total
        );
    }

    private static int offset(int page, int size) {
        return Math.max(0, page) * limit(size);
    }

    private static int limit(int size) {
        return Math.min(Math.max(1, size), 50);
    }
}
