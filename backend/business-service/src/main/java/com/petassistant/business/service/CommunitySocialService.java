package com.petassistant.business.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.petassistant.business.data.dto.internal.CommunityCommentView;
import com.petassistant.business.data.dto.internal.CommunityReportView;
import com.petassistant.business.data.dto.request.CreateCommunityCommentRequest;
import com.petassistant.business.data.dto.request.CreateCommunityReportRequest;
import com.petassistant.business.data.dto.request.ModerateCommunityReportRequest;
import com.petassistant.business.data.dto.response.CommunityAnalyticsResponse;
import com.petassistant.business.data.dto.response.CommunityCheckInResponse;
import com.petassistant.business.data.dto.response.CommunityCommentPageResponse;
import com.petassistant.business.data.dto.response.CommunityCommentResponse;
import com.petassistant.business.data.dto.response.CommunityFollowResponse;
import com.petassistant.business.data.dto.response.CommunityPostResponse;
import com.petassistant.business.data.dto.response.CommunityReactionResponse;
import com.petassistant.business.data.dto.response.CommunityReportPageResponse;
import com.petassistant.business.data.dto.response.CommunityReportResponse;
import com.petassistant.business.data.entity.CommunityCommentEntity;
import com.petassistant.business.data.entity.CommunityReportEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.data.mapper.CommunitySocialMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.CommunityInteractionConflictException;
import com.petassistant.business.exception.CommunityPostNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 评论、点赞、收藏、关注、举报和打卡业务服务。 */
@Service
public class CommunitySocialService {

    private final CommunitySocialMapper mapper;
    private final CommunityPostMapper postMapper;
    private final UserMapper userMapper;
    private final CommunityPostCacheService postCache;
    private final CommunitySocialCacheService socialCache;
    private final CommunityRecommendationService recommendationService;
    private final MessageService messageService;
    private final OutboxService outboxService;

    public CommunitySocialService(
            CommunitySocialMapper mapper,
            CommunityPostMapper postMapper,
            UserMapper userMapper,
            CommunityPostCacheService postCache,
            CommunitySocialCacheService socialCache,
            CommunityRecommendationService recommendationService,
            MessageService messageService,
            OutboxService outboxService
    ) {
        this.mapper = mapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.postCache = postCache;
        this.socialCache = socialCache;
        this.recommendationService = recommendationService;
        this.messageService = messageService;
        this.outboxService = outboxService;
    }

    @Transactional
    public CommunityReactionResponse like(String userId, String postId, boolean active) {
        //判断帖子是否存在且为公开状态
        requirePublicPost(postId);
        //判断用户是点赞还是取消点赞
        //点赞
        int changed;
        if (active)
            changed = mapper.insertLike(postId, userId, Instant.now());
        //取消点赞
        else
            changed = mapper.deleteLike(postId, userId);
        //更新帖子点赞数
        mapper.synchronizeLikeCount(postId);
        //查询帖子最终状态，防止因同步问题导致数据不一致
        boolean finalState = mapper.existsLike(postId, userId);
        //获取点赞数
        long count = mapper.countLikes(postId);
        //将点赞关系写入 Redis 等缓存层，用于快速判断"当前用户是否已点赞"
        socialCache.synchronizePostRelation(userId, postId, "likes", finalState, count);
        //刷新帖子热度
        refreshPostCaches(postId);
        //发送点赞通知
        if (active && changed > 0) {
            var post = postMapper.findPublicView(postId);
            messageService.createNotification(
                    post.authorId(), userId, "LIKE", "POST", postId,
                    "有人赞了你的动态", "你的动态《" + post.title() + "》收到一个赞。",
                    "LIKE:" + postId + ":" + userId
            );
        }
        return new CommunityReactionResponse(finalState, count);
    }

    @Transactional
    public CommunityReactionResponse favorite(String userId, String postId, boolean active) {
        requirePublicPost(postId);
        if (active) mapper.insertFavorite(postId, userId, Instant.now());
        else mapper.deleteFavorite(postId, userId);
        mapper.synchronizeFavoriteCount(postId);
        boolean finalState = mapper.existsFavorite(postId, userId);
        long count = mapper.countFavorites(postId);
        socialCache.synchronizePostRelation(userId, postId, "favorites", finalState, count);
        refreshPostCaches(postId);
        return new CommunityReactionResponse(finalState, count);
    }

    @Transactional
    public CommunityFollowResponse follow(String userId, String followedId, boolean active) {
        if (userId.equals(followedId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        UserEntity followed = userMapper.findById(followedId);
        if (followed == null || !"ACTIVE".equals(followed.status())) {
            throw new IllegalArgumentException("要关注的用户不存在或不可用");
        }
        //关注用户
        int changed;
        if (active)
            changed = mapper.insertFollow(userId, followedId, Instant.now());
        //取关用户
        else
            changed = mapper.deleteFollow(userId, followedId);
        boolean finalState = mapper.existsFollow(userId, followedId);
        //关注关系更新到缓存层
        socialCache.synchronizeFollow(userId, followedId, finalState);
        if (active && changed > 0) {
            messageService.createNotification(
                    followedId, userId, "FOLLOW", "USER", userId,
                    "你有新的关注者", "一位宠友开始关注你。", "FOLLOW:" + userId
            );
        }
        return new CommunityFollowResponse(finalState, mapper.countFollowers(followedId));
    }

    @Transactional
    public CommunityCommentResponse createComment(
            String userId,
            String postId,
            CreateCommunityCommentRequest request
    ) {
        //检查帖子是否存在且为公开状态
        requirePublicPost(postId);
        //检查回复的评论是否存在且属于当前帖子
        String parentId = blankToNull(request.parentId());
        //一级评论的根评论ID,根评论自身为null
        String rootId = null;
        //评论的深度（0表示一级评论，1表示二级评论）
        int depth = 0;
        //如果是回复评论，检查父评论是否存在且属于当前帖子
        String replyRecipientId = null;
        if (parentId != null) {
            CommunityCommentView parent = mapper.findComment(parentId);
            //parent == null → 父评论ID不存在
            //!postId.equals(parent.postId()) → 父评论不属于当前帖子（防止跨帖子回复攻击）
            if (parent == null || !postId.equals(parent.postId())) {
                throw new IllegalArgumentException("回复的评论不存在或不属于当前帖子");
            }
            //只支持两级评论
            if (parent.depth() != 0) {
                throw new IllegalArgumentException("当前只支持两级评论，请回复一级评论");
            }
            //rootId = 父评论的ID（标记这条回复属于哪个一级评论）
            //depth = 1（标识这是二级回复）
            rootId = parent.id();
            depth = 1;
            replyRecipientId = parent.authorId();
        }
        Instant now = Instant.now();
        String commentId = UUID.randomUUID().toString();
        mapper.insertComment(new CommunityCommentEntity(
                commentId, postId, userId, parentId, rootId, depth, request.content().trim(),
                "PUBLISHED", 0, now, now
        ));
        //同步帖子评论数量
        mapper.synchronizeCommentCount(postId);
        //刷新帖子缓存，包含删除缓存与更新热度分数
        refreshPostCaches(postId);
        var post = postMapper.findPublicView(postId);
        String recipientId = replyRecipientId == null ? post.authorId() : replyRecipientId;
        messageService.createNotification(
                recipientId, userId, "COMMENT", "POST", postId,
                replyRecipientId == null ? "你的动态收到评论" : "有人回复了你的评论",
                request.content().trim(), "COMMENT:" + commentId
        );
        return toCommentResponse(mapper.findComment(commentId), userId);
    }

    @Transactional(readOnly = true)
    public CommunityCommentPageResponse comments(String userId, String postId, int page, int size) {
        requirePublicPost(postId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        List<CommunityCommentResponse> items = mapper.findComments(postId, safePage * safeSize, safeSize)
                .stream().map(view -> toCommentResponse(view, userId)).toList();
        return new CommunityCommentPageResponse(items, safePage, safeSize, mapper.countComments(postId));
    }

    @Transactional
    public void deleteComment(String userId, String commentId) {
        CommunityCommentView comment = mapper.findComment(commentId);
        if (comment == null || mapper.softDeleteComment(commentId, userId, Instant.now()) == 0) {
            throw new IllegalArgumentException("评论不存在或不属于当前用户");
        }
        mapper.synchronizeCommentCount(comment.postId());
        refreshPostCaches(comment.postId());
    }

    @Transactional
    public CommunityReportResponse report(String userId, CreateCommunityReportRequest request) {
        validateReportTarget(userId, request.targetType(), request.targetId());
        Instant now = Instant.now();
        String reportId = UUID.randomUUID().toString();
        try {
            mapper.insertReport(new CommunityReportEntity(
                    reportId, userId, request.targetType(), request.targetId(), request.reasonType(),
                    blankToNull(request.description()), "PENDING", null, null, null, 1, now, null
            ));
        } catch (DuplicateKeyException exception) {
            throw new CommunityInteractionConflictException("你已经举报过该内容，请等待管理员处理");
        }
        socialCache.addReportToQueue(reportId, now.toEpochMilli());
        return toReportResponse(mapper.findReport(reportId));
    }

    @Transactional(readOnly = true)
    public CommunityReportPageResponse reports(String status, int page, int size) {
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null && !Set.of("PENDING", "RESOLVED", "REJECTED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("举报状态筛选无效");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return new CommunityReportPageResponse(
                mapper.findReportPage(normalizedStatus, safePage * safeSize, safeSize)
                        .stream().map(CommunitySocialService::toReportResponse).toList(),
                safePage, safeSize, mapper.countReports(normalizedStatus)
        );
    }

    @Transactional
    public CommunityReportResponse moderate(
            String moderatorId,
            String reportId,
            ModerateCommunityReportRequest request
    ) {
        CommunityReportView current = mapper.findReport(reportId);
        if (current == null) throw new IllegalArgumentException("举报记录不存在");
        Instant now = Instant.now();
        if (mapper.moderateReport(
                reportId, moderatorId, request.action(), blankToNull(request.note()), request.version(), now
        ) == 0) {
            throw new CommunityInteractionConflictException("举报已由其他管理员处理，请刷新列表");
        }
        if ("HIDE_CONTENT".equals(request.action())) {
            if ("POST".equals(current.targetType())) {
                mapper.hidePost(current.targetId(), now);
                outboxService.record("SEARCH_DOCUMENT", current.targetId(), "SEARCH_POST_DELETE", moderatorId);
                postCache.evict(current.targetId());
                recommendationService.remove(current.targetId());
            } else if ("COMMENT".equals(current.targetType())) {
                CommunityCommentView comment = mapper.findComment(current.targetId());
                mapper.hideComment(current.targetId(), now);
                if (comment != null) {
                    mapper.synchronizeCommentCount(comment.postId());
                    refreshPostCaches(comment.postId());
                }
            }
        }
        socialCache.removeReportFromQueue(reportId);
        messageService.createNotification(
                current.reporterId(), moderatorId, "MODERATION", current.targetType(), current.targetId(),
                "举报处理结果", "你的举报已处理，结果：" + request.action(),
                "MODERATION:" + reportId + ":" + request.version()
        );
        return toReportResponse(mapper.findReport(reportId));
    }

    @Transactional
    public CommunityCheckInResponse checkIn(String userId, LocalDate today) {
        mapper.insertCheckIn(userId, today, Instant.now());
        socialCache.recordCheckIn(userId, today);
        return checkInStatus(userId, today);
    }

    @Transactional(readOnly = true)
    public CommunityCheckInResponse checkInStatus(String userId, LocalDate today) {
        LocalDate historyStart = today.minusDays(365);
        List<LocalDate> dates = mapper.findCheckInDates(userId, historyStart, today);
        Set<LocalDate> dateSet = new HashSet<>(dates);
        int daysThisMonth = (int) dates.stream().filter(date -> YearMonth.from(date).equals(YearMonth.from(today))).count();
        int streak = 0;
        LocalDate cursor = today;
        while (dateSet.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return new CommunityCheckInResponse(today, dateSet.contains(today), daysThisMonth, streak);
    }

    @Transactional(readOnly = true)
    public CommunityAnalyticsResponse analytics(LocalDate date) {
        return new CommunityAnalyticsResponse(
                date, socialCache.approximateFeedUv(date), mapper.countPendingReports()
        );
    }

    /** 列表响应使用一次批量 SQL 查询当前用户状态，避免每张卡片三次 N+1。 */
    @Transactional(readOnly = true)
    public List<CommunityPostResponse> decoratePosts(String userId, List<CommunityPostResponse> posts) {
        if (posts.isEmpty()) return posts;
        List<String> postIds = posts.stream().map(CommunityPostResponse::id).toList();
        List<String> authorIds = posts.stream().map(CommunityPostResponse::authorId).distinct().toList();
        Set<String> likes = new HashSet<>(mapper.findLikedPostIds(userId, postIds));
        Set<String> favorites = new HashSet<>(mapper.findFavoritePostIds(userId, postIds));
        Set<String> follows = new HashSet<>(mapper.findFollowedUserIds(userId, authorIds));
        return posts.stream().map(post -> withViewerState(
                post, likes.contains(post.id()), favorites.contains(post.id()), follows.contains(post.authorId())
        )).toList();
    }

    private void refreshPostCaches(String postId) {
        postCache.evict(postId);
        recommendationService.refreshHotScore(postMapper.findPublicView(postId));
    }

    private void requirePublicPost(String postId) {
        if (!mapper.existsPublicPost(postId)) throw new CommunityPostNotFoundException();
    }

    private void validateReportTarget(String userId, String targetType, String targetId) {
        if ("POST".equals(targetType)) {
            requirePublicPost(targetId);
        } else if ("COMMENT".equals(targetType)) {
            if (mapper.findComment(targetId) == null) throw new IllegalArgumentException("举报的评论不存在");
        } else {
            UserEntity target = userMapper.findById(targetId);
            if (target == null) throw new IllegalArgumentException("举报的用户不存在");
            if (userId.equals(targetId)) throw new IllegalArgumentException("不能举报自己");
        }
    }

    private static CommunityCommentResponse toCommentResponse(CommunityCommentView view, String userId) {
        return new CommunityCommentResponse(
                view.id(), view.postId(), view.authorId(), view.authorUsername(), view.authorDisplayName(),
                view.parentId(), view.rootId(), view.depth(), view.content(), view.likeCount(),
                userId.equals(view.authorId()), view.createdAt(), view.updatedAt()
        );
    }

    private static CommunityReportResponse toReportResponse(CommunityReportView view) {
        return new CommunityReportResponse(
                view.id(), view.reporterId(), view.reporterUsername(), view.targetType(), view.targetId(),
                view.reasonType(), view.description(), view.status(), view.resolution(), view.moderatorId(),
                view.moderatorUsername(), view.moderatorNote(), view.version(), view.createdAt(), view.resolvedAt()
        );
    }

    private static CommunityPostResponse withViewerState(
            CommunityPostResponse post,
            boolean liked,
            boolean favorited,
            boolean followsAuthor
    ) {
        return new CommunityPostResponse(
                post.id(), post.authorId(), post.authorUsername(), post.authorDisplayName(), post.petProfileId(),
                post.petName(), post.topicId(), post.topicName(), post.title(), post.content(), post.region(),
                post.latitude(), post.longitude(), post.status(), post.viewCount(), post.likeCount(),
                post.commentCount(), post.favoriteCount(), post.version(), post.publishedAt(), post.createdAt(),
                post.updatedAt(), post.media(), liked, favorited, followsAuthor
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
