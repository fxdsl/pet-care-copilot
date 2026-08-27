package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.internal.PostValueRow;
import com.petassistant.business.data.dto.request.CreateCommunityRepostRequest;
import com.petassistant.business.data.dto.response.CommunityRelationControlResponse;
import com.petassistant.business.data.dto.response.CommunityRepostResponse;
import com.petassistant.business.data.dto.response.RecommendationFeedbackResponse;
import com.petassistant.business.data.dto.response.RecommendationItemResponse;
import com.petassistant.business.data.dto.response.RecommendationPageResponse;
import com.petassistant.business.data.entity.CommunityRelationControlEntity;
import com.petassistant.business.data.entity.CommunityRepostEntity;
import com.petassistant.business.data.entity.RecommendationFeedbackEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityGovernanceMapper;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.CommunitySocialMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.CommunityPostNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 转发、屏蔽/拉黑和可解释推荐的事务编排。 */
@Service
public class CommunityGovernanceService {

    private final CommunityGovernanceMapper mapper;
    private final CommunityPostMapper postMapper;
    private final CommunitySocialMapper socialMapper;
    private final UserMapper userMapper;
    private final CommunityPostService postService;
    private final CommunitySocialService socialService;
    private final CommunityPostCacheService postCache;
    private final CommunitySocialCacheService socialCache;
    private final CommunityGovernanceCacheService cache;
    private final OutboxService outboxService;

    public CommunityGovernanceService(
            CommunityGovernanceMapper mapper,
            CommunityPostMapper postMapper,
            CommunitySocialMapper socialMapper,
            UserMapper userMapper,
            CommunityPostService postService,
            CommunitySocialService socialService,
            CommunityPostCacheService postCache,
            CommunitySocialCacheService socialCache,
            CommunityGovernanceCacheService cache,
            OutboxService outboxService
    ) {
        this.mapper = mapper;
        this.postMapper = postMapper;
        this.socialMapper = socialMapper;
        this.userMapper = userMapper;
        this.postService = postService;
        this.socialService = socialService;
        this.postCache = postCache;
        this.socialCache = socialCache;
        this.cache = cache;
        this.outboxService = outboxService;
    }

    /** PUT/DELETE 都设置目标状态，唯一约束保证重复请求不增加转发计数。 */
    @Transactional
    public CommunityRepostResponse repost(
            String userId, String postId, boolean active, CreateCommunityRepostRequest request
    ) {
        CommunityPostView post = postMapper.findPublicView(postId);
        if (post == null) throw new CommunityPostNotFoundException();
        if (mapper.existsBlockEitherDirection(userId, post.authorId())) {
            throw new IllegalArgumentException("拉黑关系生效期间不能转发该用户内容");
        }
        CommunityRepostEntity existing = mapper.findRepost(postId, userId);
        Instant now = Instant.now();
        CommunityRepostEntity saved = new CommunityRepostEntity(
                existing == null ? UUID.randomUUID().toString() : existing.id(), postId, userId,
                request == null || request.quoteContent() == null || request.quoteContent().isBlank()
                        ? null : request.quoteContent().trim(),
                active, existing == null ? now : existing.createdAt(), now
        );
        mapper.upsertRepost(saved);
        mapper.synchronizeRepostCount(postId);
        CommunityRepostEntity current = mapper.findRepost(postId, userId);
        cache.synchronizeRepost(current);
        postCache.evict(postId);
        outboxService.record("COMMUNITY_REPOST", current.id(),
                current.active() ? "COMMUNITY_REPOSTED" : "COMMUNITY_REPOST_REMOVED", userId);
        return new CommunityRepostResponse(current.active(), mapper.countReposts(postId));
    }

    /** BLOCK 同时解除双方关注；MUTE 只影响发起者的信息流。 */
    @Transactional
    public CommunityRelationControlResponse relation(
            String userId, String targetUserId, String relationType, boolean active
    ) {
        String type = relationType == null ? "" : relationType.trim().toUpperCase();
        if (!Set.of("MUTE", "BLOCK").contains(type)) throw new IllegalArgumentException("关系类型只允许 MUTE 或 BLOCK");
        if (userId.equals(targetUserId)) throw new IllegalArgumentException("不能屏蔽或拉黑自己");
        UserEntity target = userMapper.findById(targetUserId);
        if (target == null || !"ACTIVE".equals(target.status())) throw new IllegalArgumentException("目标用户不存在或不可用");
        CommunityRelationControlEntity existing = mapper.findRelation(userId, targetUserId, type);
        Instant now = Instant.now();
        CommunityRelationControlEntity saved = new CommunityRelationControlEntity(
                existing == null ? UUID.randomUUID().toString() : existing.id(), userId, targetUserId,
                type, active, existing == null ? now : existing.createdAt(), now
        );
        mapper.upsertRelation(saved);
        if (active && "BLOCK".equals(type)) {
            socialMapper.deleteFollow(userId, targetUserId);
            socialMapper.deleteFollow(targetUserId, userId);
            socialCache.synchronizeFollow(userId, targetUserId, false);
            socialCache.synchronizeFollow(targetUserId, userId, false);
        }
        CommunityRelationControlEntity current = mapper.findRelation(userId, targetUserId, type);
        cache.synchronizeRelation(current);
        outboxService.record("COMMUNITY_RELATION", current.id(),
                active ? "COMMUNITY_" + type + "_ENABLED" : "COMMUNITY_" + type + "_DISABLED", userId);
        return new CommunityRelationControlResponse(targetUserId, type, current.active());
    }

    @Transactional
    public RecommendationFeedbackResponse notInterested(String userId, String postId, boolean active) {
        if (postMapper.findPublicView(postId) == null) throw new CommunityPostNotFoundException();
        RecommendationFeedbackEntity existing = mapper.findFeedback(userId, postId);
        Instant now = Instant.now();
        RecommendationFeedbackEntity saved = new RecommendationFeedbackEntity(
                existing == null ? UUID.randomUUID().toString() : existing.id(), userId, postId,
                "NOT_INTERESTED", active, existing == null ? now : existing.createdAt(), now
        );
        mapper.upsertFeedback(saved);
        RecommendationFeedbackEntity current = mapper.findFeedback(userId, postId);
        cache.synchronizeFeedback(current);
        outboxService.record("RECOMMENDATION_FEEDBACK", current.id(),
                active ? "RECOMMENDATION_NOT_INTERESTED" : "RECOMMENDATION_FEEDBACK_REVOKED", userId);
        return new RecommendationFeedbackResponse(postId, current.active());
    }

    /** 关注、宠物类型、话题偏好、内容质量与时间衰减共同排序。 */
    @Transactional(readOnly = true)
    public RecommendationPageResponse recommendations(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<CommunityPostView> candidates = postMapper.findHotCandidates(300);
        if (candidates.isEmpty()) return new RecommendationPageResponse(List.of(), safePage, safeSize, 0);
        List<String> postIds = candidates.stream().map(CommunityPostView::id).toList();
        Set<String> excludedAuthors = new HashSet<>(mapper.findExcludedAuthorIds(userId));
        Set<String> ignoredPosts = new HashSet<>(mapper.findNotInterestedPostIds(userId, postIds));
        Set<String> preferredTopics = new HashSet<>(mapper.findPreferredTopicIds(userId, 5));
        Set<String> petTypes = new HashSet<>(mapper.findPetTypes(userId));
        Set<String> followed = new HashSet<>(socialMapper.findFollowedUserIds(
                userId, candidates.stream().map(CommunityPostView::authorId).distinct().toList()
        ));
        Map<String, String> postPetTypes = new HashMap<>();
        for (PostValueRow row : mapper.findPostPetTypes(postIds)) postPetTypes.put(row.postId(), row.value());

        List<Scored> scored = new ArrayList<>();
        for (CommunityPostView post : candidates) {
            if (post.authorId().equals(userId) || excludedAuthors.contains(post.authorId()) || ignoredPosts.contains(post.id())) continue;
            double ageHours = Math.max(0, Duration.between(post.publishedAt(), Instant.now()).toMinutes() / 60.0);
            double score = (post.likeCount() * 3 + post.commentCount() * 4 + post.favoriteCount() * 5
                    + post.repostCount() * 6 + Math.log1p(post.viewCount())) / Math.pow(ageHours + 2, 1.1);
            String reason = "近期优质内容";
            if (followed.contains(post.authorId())) { score += 80; reason = "来自你关注的宠友"; }
            else if (post.petProfileId() != null && petTypes.contains(postPetTypes.get(post.id()))) {
                score += 45; reason = "与你的宠物类型相关";
            } else if (post.topicId() != null && preferredTopics.contains(post.topicId())) {
                score += 30; reason = "符合你常看的话题";
            }
            scored.add(new Scored(post, score, reason));
        }
        scored.sort((left, right) -> Double.compare(right.score(), left.score()));
        cache.cacheRecommendations(userId, scored.stream().map(item ->
                new CommunityGovernanceCacheService.ScoredRecommendation(item.post().id(), item.score(), item.reason())
        ).toList());
        int from = Math.min(safePage * safeSize, scored.size());
        int to = Math.min(from + safeSize, scored.size());
        var base = scored.subList(from, to);
        var decorated = socialService.decoratePosts(userId, base.stream().map(item -> postService.toResponse(item.post())).toList());
        List<RecommendationItemResponse> items = new ArrayList<>();
        for (int index = 0; index < base.size(); index++) {
            items.add(new RecommendationItemResponse(decorated.get(index), base.get(index).score(), base.get(index).reason(), false));
        }
        return new RecommendationPageResponse(items, safePage, safeSize, scored.size());
    }

    public boolean blocksEitherDirection(String firstUserId, String secondUserId) {
        return mapper.existsBlockEitherDirection(firstUserId, secondUserId);
    }

    private record Scored(CommunityPostView post, double score, String reason) { }
}
