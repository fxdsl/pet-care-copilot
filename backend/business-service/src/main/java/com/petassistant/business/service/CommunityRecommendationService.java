package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.petassistant.business.data.dto.internal.CommunityPostView;
import com.petassistant.business.data.mapper.CommunityPostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 热门、关注和附近信息流；Redis 丢失时从 MySQL 重建或回源。 */
@Service
public class CommunityRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(CommunityRecommendationService.class);
    private static final String HOT_KEY = "community:feed:hot";

    private final CommunityPostMapper postMapper;
    private final CommunitySocialCacheService cache;
    private final StringRedisTemplate redisTemplate;

    public CommunityRecommendationService(CommunityPostMapper postMapper, CommunitySocialCacheService cache) {
        this.postMapper = postMapper;
        this.cache = cache;
        this.redisTemplate = cache.redisTemplate();
    }

    public FeedSlice following(String userId, String topicId, int page, int size) {
        List<CommunityPostView> items = postMapper.findFollowingPage(userId, topicId, page * size, size);
        return new FeedSlice(items, postMapper.countFollowing(userId, topicId));
    }

    public FeedSlice hot(int page, int size, String topicId) {
        ensureHotRanking();
        try {
            int fetchSize = Math.min(Math.max((page + 1) * size * 4, 50), 500);
            Set<String> ranked = redisTemplate.opsForZSet().reverseRange(HOT_KEY, 0, fetchSize - 1L);
            if (ranked == null || ranked.isEmpty()) return hotFromDatabase(page, size, topicId);
            List<String> ids = new ArrayList<>(ranked);
            List<CommunityPostView> views = postMapper.findPublicByIds(ids);
            List<CommunityPostView> sorted = orderByIds(views, ids).stream()
                    .filter(view -> topicId == null || topicId.equals(view.topicId()))
                    .toList();
            int from = Math.min(page * size, sorted.size());
            int to = Math.min(from + size, sorted.size());
            return new FeedSlice(sorted.subList(from, to), sorted.size());
        } catch (RuntimeException exception) {
            log.warn("Hot feed cache read failed, fallback to MySQL: {}", exception.toString());
            return hotFromDatabase(page, size, topicId);
        }
    }

    public FeedSlice nearby(
            double latitude,
            double longitude,
            double radiusKm,
            String topicId,
            int page,
            int size
    ) {
        try {
            var circle = new org.springframework.data.geo.Circle(
                    new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS)
            );
            long fetchSize = Math.min(Math.max((page + 1L) * size * 4, 50), 500);
            var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                    .includeDistance().sortAscending().limit(fetchSize);
            var results = redisTemplate.opsForGeo().radius(CommunitySocialCacheService.geoKey(), circle, args);
            if (results != null && !results.getContent().isEmpty()) {
                List<String> ids = results.getContent().stream()
                        .map(result -> result.getContent().getName()).toList();
                // GEO 只负责距离排序，话题仍以 MySQL 帖子字段为准，避免在 Redis 重复保存业务属性。
                List<CommunityPostView> ordered = orderByIds(postMapper.findPublicByIds(ids), ids).stream()
                        .filter(view -> topicId == null || topicId.equals(view.topicId()))
                        .toList();
                int from = Math.min(page * size, ordered.size());
                int to = Math.min(from + size, ordered.size());
                return new FeedSlice(ordered.subList(from, to), ordered.size());
            }
        } catch (RuntimeException exception) {
            log.warn("Nearby GEO read failed, fallback to MySQL: {}", exception.toString());
        }
        List<CommunityPostView> fallback = postMapper.findNearby(
                latitude, longitude, radiusKm, topicId, Math.min((page + 1) * size, 500)
        );
        int from = Math.min(page * size, fallback.size());
        int to = Math.min(from + size, fallback.size());
        return new FeedSlice(fallback.subList(from, to), fallback.size());
    }

    /** 发布或互动变化后刷新单帖热度，时间衰减避免旧内容永久霸榜。
     *                     互动总分
     * 热度 Score = ──────────────────────────
     *             (年龄小时数 + 2) ^ 1.15
     * */
    public void refreshHotScore(CommunityPostView post) {
        if (post == null || !"PUBLISHED".equals(post.status())) return;
        //计算帖子年龄
        double ageHours = Math.max(0, Duration.between(post.publishedAt(), Instant.now()).toMinutes() / 60.0);
        //计算互动总分（分子）
        double engagement = post.likeCount() * 3.0  // 点赞权重
            + post.commentCount() * 4.0// 评论权重
            + post.favoriteCount() * 5.0 // 收藏权重
            + post.repostCount() * 6.0 // 转发代表更强的传播意愿
            + Math.log1p(post.viewCount());// 浏览量权重（对数压缩）
        double score = engagement / Math.pow(ageHours + 2.0, 1.15);
        try {
            //按照热度排序
            redisTemplate.opsForZSet().add(HOT_KEY, post.id(), score);
            //缓存 6 小时
            redisTemplate.expire(HOT_KEY, Duration.ofHours(6));
        } catch (DataAccessException exception) {
            log.warn("Hot score update failed: {}", exception.toString());
        }
    }

    public void remove(String postId) {
        try {
            redisTemplate.opsForZSet().remove(HOT_KEY, postId);
        } catch (DataAccessException exception) {
            log.warn("Hot score removal failed: {}", exception.toString());
        }
        cache.removeLocation(postId);
    }

    /** 启动或缓存失效后从 MySQL 重建热榜和 GEO，不把 Redis 当事实来源。 */
    public void rebuild() {
        try {
            redisTemplate.delete(List.of(HOT_KEY, CommunitySocialCacheService.geoKey()));
            for (CommunityPostView post : postMapper.findHotCandidates(500)) refreshHotScore(post);
            for (CommunityPostView post : postMapper.findPublishedWithLocation()) {
                cache.addLocation(post.id(), post.latitude(), post.longitude());
            }
        } catch (RuntimeException exception) {
            log.warn("Community recommendation rebuild skipped: {}", exception.toString());
        }
    }

    public void recordVisit(String userId) {
        cache.recordFeedVisitor(userId, LocalDate.now());
    }

    private void ensureHotRanking() {
        try {
            Long size = redisTemplate.opsForZSet().zCard(HOT_KEY);
            if (size == null || size == 0) rebuild();
        } catch (DataAccessException exception) {
            log.warn("Hot feed readiness check failed: {}", exception.toString());
        }
    }

    private FeedSlice hotFromDatabase(int page, int size, String topicId) {
        List<CommunityPostView> candidates = postMapper.findHotCandidates(500).stream()
                .filter(view -> topicId == null || topicId.equals(view.topicId()))
                .sorted(Comparator.comparingDouble(CommunityRecommendationService::databaseScore).reversed())
                .toList();
        int from = Math.min(page * size, candidates.size());
        int to = Math.min(from + size, candidates.size());
        return new FeedSlice(candidates.subList(from, to), candidates.size());
    }

    private static double databaseScore(CommunityPostView post) {
        double ageHours = Math.max(0, Duration.between(post.publishedAt(), Instant.now()).toMinutes() / 60.0);
        return (post.likeCount() * 3.0 + post.commentCount() * 4.0 + post.favoriteCount() * 5.0
                + post.repostCount() * 6.0
                + Math.log1p(post.viewCount())) / Math.pow(ageHours + 2.0, 1.15);
    }

    private static List<CommunityPostView> orderByIds(List<CommunityPostView> views, List<String> ids) {
        Map<String, CommunityPostView> byId = new HashMap<>();
        for (CommunityPostView view : views) byId.put(view.id(), view);
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 推荐服务只返回数据库投影，媒体与当前用户关系仍由帖子服务组装。 */
    public record FeedSlice(List<CommunityPostView> items, long total) { }
}
