package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.dto.request.CreateCommunityPostRequest;
import com.petassistant.business.data.dto.request.UpdateCommunityPostRequest;
import com.petassistant.business.data.dto.response.CommunityMediaResponse;
import com.petassistant.business.data.dto.response.CommunityPostPageResponse;
import com.petassistant.business.data.dto.response.CommunityPostResponse;
import com.petassistant.business.data.dto.response.CommunityTopicResponse;
import com.petassistant.business.data.entity.CommunityMediaEntity;
import com.petassistant.business.data.entity.CommunityPostEntity;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import com.petassistant.business.exception.CommunityPostConflictException;
import com.petassistant.business.exception.CommunityPostNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 社区帖子草稿、发布、编辑、删除、公开详情和媒体绑定服务。 */
@Service
public class CommunityPostService {

    private final CommunityPostMapper postMapper;
    private final CommunityMediaMapper mediaMapper;
    private final PetProfileService petProfileService;
    private final CommunityPostCacheService cache;
    private final OutboxService outboxService;
    private final CommunitySocialService socialService;
    private final CommunityRecommendationService recommendationService;
    private final CommunitySocialCacheService socialCache;

    public CommunityPostService(
            CommunityPostMapper postMapper,
            CommunityMediaMapper mediaMapper,
            PetProfileService petProfileService,
            CommunityPostCacheService cache,
            OutboxService outboxService,
            CommunitySocialService socialService,
            CommunityRecommendationService recommendationService,
            CommunitySocialCacheService socialCache
    ) {
        this.postMapper = postMapper;
        this.mediaMapper = mediaMapper;
        this.petProfileService = petProfileService;
        this.cache = cache;
        this.outboxService = outboxService;
        this.socialService = socialService;
        this.recommendationService = recommendationService;
        this.socialCache = socialCache;
    }

    /** 新帖子先保存为 DRAFT，只有作者显式发布后才进入公开列表。 */
    @Transactional
    public CommunityPostResponse create(String userId, CreateCommunityPostRequest request) {
        validateRelations(userId, request.petProfileId(), request.topicId());
        validateCoordinates(request.latitude(), request.longitude());
        //上传的媒体文件 ID 列表，用于绑定到帖子实体
        List<String> mediaIds = normalizedMediaIds(request.mediaIds());
        //创建时间
        Instant now = Instant.now();
        //随机生成帖子 ID
        String postId = UUID.randomUUID().toString();
        //插入帖子实体，状态为 DRAFT，这个代表帖子为草稿状态
        postMapper.insert(new CommunityPostEntity(
                postId, userId, blankToNull(request.petProfileId()), blankToNull(request.topicId()),
                request.title().trim(), request.content().trim(), blankToNull(request.region()),
                request.latitude(), request.longitude(),
                "DRAFT", 0, 0, 0, 0, 1, null, now, now
        ));
        //绑定媒体文件实体到帖子实体
        replaceMedia(userId, postId, mediaIds);
        return requireOwnedResponse(userId, postId);
    }

    /** 使用 version 乐观锁更新，避免两个浏览器标签互相覆盖。 */
    @Transactional
    public CommunityPostResponse update(String userId, String postId, UpdateCommunityPostRequest request) {
        CommunityPostEntity current = requireOwned(userId, postId);
        String title = request.title() == null ? current.title() : request.title().trim();
        String content = request.content() == null ? current.content() : request.content().trim();
        String petId = request.petProfileId() == null ? current.petProfileId() : blankToNull(request.petProfileId());
        String topicId = request.topicId() == null ? current.topicId() : blankToNull(request.topicId());
        String region = request.region() == null ? current.region() : blankToNull(request.region());
        Double latitude = request.latitude() == null ? current.latitude() : request.latitude();
        Double longitude = request.longitude() == null ? current.longitude() : request.longitude();
        validateCoordinates(latitude, longitude);
        validateRelations(userId, petId, topicId);
        if (postMapper.updateOwned(
                postId, userId, title, content, petId, topicId, region, latitude, longitude,
                request.version(), Instant.now()
        ) == 0) throw new CommunityPostConflictException();
        if (request.mediaIds() != null) replaceMedia(userId, postId, normalizedMediaIds(request.mediaIds()));
        if ("PUBLISHED".equals(current.status())) {
            outboxService.record("SEARCH_DOCUMENT", postId, "SEARCH_POST_UPSERT", userId);
        }
        cache.evict(postId);
        return requireOwnedResponse(userId, postId);
    }

    /** 发布动作写入 Outbox；RabbitMQ 不可用时帖子仍提交，事件稍后重试。 */
    @Transactional
    public CommunityPostResponse publish(String userId, String postId) {
        //防止有其他用户对不属于自己的帖子执行了发布流程
        requireOwned(userId, postId);
        //更新帖子实体状态为 PUBLISHED，这个代表帖子已发布
        if (postMapper.publishOwned(postId, userId, Instant.now()) == 0)
            throw new CommunityPostNotFoundException();
        //将数据写入 Outbox，这个代表帖子已发布，后续会触发 RabbitMQ 发送事件。
        outboxService.record("COMMUNITY_POST", postId, "POST_PUBLISHED", userId);
        outboxService.record("SEARCH_DOCUMENT", postId, "SEARCH_POST_UPSERT", userId);
        // 删除该帖子的详情缓存
        // 因为帖子状态已经从草稿变成已发布，旧缓存可能还是发布前的数据；
        // 清除后，下次访问会从 MySQL 查询最新数据并重新缓存。
        cache.evict(postId);
        //把帖子 ID 加入布隆过滤器。公开查询帖子时会先执行。
        cache.addPublishedId(postId);
        CommunityPostResponse response = requireOwnedResponse(userId, postId);
        //将帖子的地理位置信息缓存到 Redis 中，后续查询时会先从 Redis 中获取
        socialCache.addLocation(postId, response.latitude(), response.longitude());
        //刷新帖子的热度分数
        recommendationService.refreshHotScore(postMapper.findPublicView(postId));
        return response;
    }

    /** 逻辑删除保留审计数据，同时解绑媒体，后续不再签发公开下载地址。 */
    @Transactional
    public void delete(String userId, String postId) {
        requireOwned(userId, postId);
        if (postMapper.softDeleteOwned(postId, userId, Instant.now()) == 0) throw new CommunityPostNotFoundException();
        mediaMapper.detachFromPost(postId, userId);
        outboxService.record("SEARCH_DOCUMENT", postId, "SEARCH_POST_DELETE", userId);
        cache.evict(postId);
        recommendationService.remove(postId);
    }

    /** 公开详情使用 Cache-Aside；浏览数先写 Redis Hash，定时批量回写 MySQL。 */
    @Transactional(readOnly = true)
    public CommunityPostResponse publicDetail(String postId) {
        //利用布隆过滤器快速判断帖子是否存在
        //如果帖子不存在，直接抛出异常
        if (!cache.mightExist(postId))
            throw new CommunityPostNotFoundException();
        //从缓存中获取帖子详情
        //如果缓存中不存在帖子详情，从 MySQL 查询帖子详情
        CommunityPostResponse response = cache.get(postId);
        if (response == null) {
            //从 MySQL 查询帖子详情
            CommunityPostView view = postMapper.findPublicView(postId);
            if (view == null) throw new CommunityPostNotFoundException();
            response = toResponse(view);
            //将帖子详情缓存到 Redis 中
            cache.put(response);
        }
        //增加帖子浏览数
        //将增加的浏览数写入 Redis Hash，定时批量回写 MySQL
        long pendingViews = cache.incrementView(postId);
        //返回帖子详情，包含增加的浏览数
        return withViewCount(response, response.viewCount() + pendingViews);
    }

    /** 当前用户详情额外附带点赞、收藏和作者关注状态；这些字段不进入共享详情缓存。 */
    @Transactional(readOnly = true)
    public CommunityPostResponse publicDetail(String userId, String postId) {
        return socialService.decoratePosts(userId, List.of(publicDetail(postId))).get(0);
    }

    @Transactional(readOnly = true)
    public CommunityPostPageResponse publicPosts(String topicId, String authorId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String safeTopic = blankToNull(topicId);
        String safeAuthor = blankToNull(authorId);
        List<CommunityPostResponse> items = postMapper.findPublicPage(
                safeTopic, safeAuthor, safePage * safeSize, safeSize
        ).stream().map(this::toResponse).toList();
        return new CommunityPostPageResponse(
                items, safePage, safeSize, postMapper.countPublic(safeTopic, safeAuthor)
        );
    }

    /** 第九周四类信息流；LATEST 直接分页，其他类型由推荐服务回源或读取可重建缓存。 */
    @Transactional(readOnly = true)
    public CommunityPostPageResponse publicFeed(
            String userId,
            String feed,
            String topicId,
            String authorId,
            Double latitude,
            Double longitude,
            double radiusKm,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedFeed = feed == null || feed.isBlank() ? "LATEST" : feed.trim().toUpperCase();
        String safeTopic = blankToNull(topicId);
        recommendationService.recordVisit(userId);
        List<CommunityPostResponse> items;
        long total;
        if ("LATEST".equals(normalizedFeed)) {
            CommunityPostPageResponse latest = publicPosts(safeTopic, blankToNull(authorId), safePage, safeSize);
            items = latest.items();
            total = latest.total();
        } else if ("FOLLOWING".equals(normalizedFeed)) {
            var slice = recommendationService.following(userId, safeTopic, safePage, safeSize);
            items = slice.items().stream().map(this::toResponse).toList();
            total = slice.total();
        } else if ("HOT".equals(normalizedFeed)) {
            var slice = recommendationService.hot(safePage, safeSize, safeTopic);
            items = slice.items().stream().map(this::toResponse).toList();
            total = slice.total();
        } else if ("NEARBY".equals(normalizedFeed)) {
            if (latitude == null || longitude == null) {
                throw new IllegalArgumentException("附近信息流需要 latitude 和 longitude");
            }
            validateCoordinates(latitude, longitude);
            double safeRadius = Math.min(Math.max(radiusKm, 1), 100);
            var slice = recommendationService.nearby(
                    latitude, longitude, safeRadius, safeTopic, safePage, safeSize
            );
            items = slice.items().stream().map(this::toResponse).toList();
            total = slice.total();
        } else {
            throw new IllegalArgumentException("feed 只支持 LATEST、HOT、FOLLOWING 或 NEARBY");
        }
        return new CommunityPostPageResponse(
                socialService.decoratePosts(userId, items), safePage, safeSize, total
        );
    }

    @Transactional(readOnly = true)
    public CommunityPostPageResponse mine(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return new CommunityPostPageResponse(
                postMapper.findMinePage(userId, safePage * safeSize, safeSize)
                        .stream().map(this::toResponse).toList(),
                safePage, safeSize, postMapper.countMine(userId)
        );
    }

    @Transactional(readOnly = true)
    public List<CommunityTopicResponse> topics() {
        return postMapper.findActiveTopics();
    }

    private void validateRelations(String userId, String petProfileId, String topicId) {
        String petId = blankToNull(petProfileId);
        if (petId != null) petProfileService.requireEntity(userId, petId);
        String normalizedTopic = blankToNull(topicId);
        if (normalizedTopic != null && !postMapper.existsActiveTopic(normalizedTopic)) {
            throw new IllegalArgumentException("所选社区话题不存在或已停用");
        }
    }
    //用于绑定媒体文件实体到帖子实体
    private void replaceMedia(String userId, String postId, List<String> mediaIds) {
        //这一步是用于编辑帖子时，解绑旧的媒体文件实体，避免重复绑定或绑定错误的媒体文件。
        mediaMapper.detachFromPost(postId, userId);
        if (mediaIds.isEmpty()) return;
        if (mediaMapper.countAttachable(userId, mediaIds) != mediaIds.size()) {
            throw new IllegalArgumentException("存在未确认、已被使用或不属于当前用户的媒体");
        }
        if (mediaMapper.attachToPost(postId, userId, mediaIds) != mediaIds.size()) {
            throw new IllegalArgumentException("媒体绑定失败，请刷新后重试");
        }
    }

    private CommunityPostEntity requireOwned(String userId, String postId) {
        CommunityPostEntity post = postMapper.findOwnedEntity(postId, userId);
        if (post == null) throw new CommunityPostNotFoundException();
        return post;
    }

    private CommunityPostResponse requireOwnedResponse(String userId, String postId) {
        CommunityPostView view = postMapper.findOwnedView(postId, userId);
        if (view == null) throw new CommunityPostNotFoundException();
        return toResponse(view);
    }

    /** 将公开帖子联表投影转换为统一响应，供个人关系分页复用同一媒体装配规则。 */
    public CommunityPostResponse toResponse(CommunityPostView view) {
        List<CommunityMediaResponse> media = mediaMapper.findByPostId(view.id()).stream()
                .map(CommunityPostService::toMediaResponse).toList();
        return new CommunityPostResponse(
                view.id(), view.authorId(), view.authorUsername(), view.authorDisplayName(),
                view.petProfileId(), view.petName(), view.topicId(), view.topicName(),
                view.title(), view.content(), view.region(), view.latitude(), view.longitude(),
                view.status(), view.viewCount(),
                view.likeCount(), view.commentCount(), view.favoriteCount(), view.version(),
                view.publishedAt(), view.createdAt(), view.updatedAt(), media, false, false, false
        );
    }

    private static CommunityMediaResponse toMediaResponse(CommunityMediaEntity media) {
        return new CommunityMediaResponse(
                media.id(), media.mediaType(), media.contentType(), media.originalFilename(),
                media.sizeBytes(), media.status(), media.processingStatus(), media.confirmedAt()
        );
    }

    private static CommunityPostResponse withViewCount(CommunityPostResponse response, long viewCount) {
        return new CommunityPostResponse(
                response.id(), response.authorId(), response.authorUsername(), response.authorDisplayName(),
                response.petProfileId(), response.petName(), response.topicId(), response.topicName(),
                response.title(), response.content(), response.region(), response.latitude(), response.longitude(),
                response.status(), viewCount,
                response.likeCount(), response.commentCount(), response.favoriteCount(), response.version(),
                response.publishedAt(), response.createdAt(), response.updatedAt(), response.media(),
                response.viewerLiked(), response.viewerFavorited(), response.viewerFollowsAuthor()
        );
    }

    private static List<String> normalizedMediaIds(List<String> mediaIds) {
        if (mediaIds == null) return List.of();
        List<String> normalized = mediaIds.stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (normalized.size() > 6) throw new IllegalArgumentException("每篇帖子最多关联 6 个媒体文件");
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("经纬度必须同时填写或同时留空");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("经纬度超出有效范围");
        }
    }
}
