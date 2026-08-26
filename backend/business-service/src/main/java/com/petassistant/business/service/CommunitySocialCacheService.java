package com.petassistant.business.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 第九周 Redis 副本：Set 关系、ZSet 审核优先级、GEO 位置、Bitmap 打卡和 HyperLogLog UV。
 * 所有写入都允许失败，因为 MySQL 才是最终事实来源。
 */
@Service
public class CommunitySocialCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunitySocialCacheService.class);
    private static final String GEO_KEY = "community:post:geo";
    private static final String REPORT_QUEUE_KEY = "community:report:queue";
    private static final Duration RELATION_TTL = Duration.ofDays(30);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DefaultRedisScript<Long> SYNC_RELATION = new DefaultRedisScript<>(
            "if ARGV[1] == '1' then redis.call('SADD', KEYS[1], ARGV[2]) "
                    + "else redis.call('SREM', KEYS[1], ARGV[2]) end; "
                    + "redis.call('HSET', KEYS[2], ARGV[3], ARGV[4]); return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public CommunitySocialCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Lua 同时更新用户关系 Set 与帖子计数快照 Hash，避免前端短暂看到不一致。 */
    public void synchronizePostRelation(
            String userId,
            String postId,
            String relation,
            boolean active,
            long databaseCount
    ) {
        String relationKey = "community:user:" + userId + ":" + relation;
        String counterKey = "community:post:social:" + postId;
        String counterField = relation.equals("likes") ? "likeCount" : "favoriteCount";
        try {
            redisTemplate.execute(
                    SYNC_RELATION,
                    List.of(relationKey, counterKey),
                    active ? "1" : "0", postId, counterField, Long.toString(databaseCount)
            );
            // 关系集合是 MySQL 的可重建副本，续期而不是永久占用 Redis 内存。
            redisTemplate.expire(relationKey, RELATION_TTL);
            redisTemplate.expire(counterKey, Duration.ofHours(6));
        } catch (RuntimeException exception) {
            log.warn("Community relation cache sync failed: {}", exception.toString());
        }
    }

    public void synchronizeFollow(String followerId, String followedId, boolean following) {
        try {
            String key = "community:user:" + followerId + ":following";
            if (following) redisTemplate.opsForSet().add(key, followedId);
            else redisTemplate.opsForSet().remove(key, followedId);
            redisTemplate.expire(key, RELATION_TTL);
        } catch (DataAccessException exception) {
            log.warn("Community follow cache sync failed: {}", exception.toString());
        }
    }

    public void addLocation(String postId, Double latitude, Double longitude) {
        if (latitude == null || longitude == null) return;
        try {
            redisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), postId);
        } catch (DataAccessException exception) {
            log.warn("Community GEO update failed: {}", exception.toString());
        }
    }

    public void removeLocation(String postId) {
        try {
            redisTemplate.opsForGeo().remove(GEO_KEY, postId);
        } catch (DataAccessException exception) {
            log.warn("Community GEO removal failed: {}", exception.toString());
        }
    }

    public void addReportToQueue(String reportId, long epochMillis) {
        try {
            redisTemplate.opsForZSet().add(REPORT_QUEUE_KEY, reportId, epochMillis);
        } catch (DataAccessException exception) {
            log.warn("Community report queue update failed: {}", exception.toString());
        }
    }

    public void removeReportFromQueue(String reportId) {
        try {
            redisTemplate.opsForZSet().remove(REPORT_QUEUE_KEY, reportId);
        } catch (DataAccessException exception) {
            log.warn("Community report queue removal failed: {}", exception.toString());
        }
    }

    /** dayOfMonth-1 作为 Bitmap offset；Key 保留 14 个月便于教学查看。 */
    public void recordCheckIn(String userId, LocalDate date) {
        try {
            String key = "community:checkin:" + userId + ":" + YearMonth.from(date);
            redisTemplate.opsForValue().setBit(key, date.getDayOfMonth() - 1L, true);
            redisTemplate.expire(key, Duration.ofDays(430));
        } catch (DataAccessException exception) {
            log.warn("Community check-in bitmap update failed: {}", exception.toString());
        }
    }

    /** HLL 只用于近似趋势，不参与权限、计费或最终人数。 */
    public void recordFeedVisitor(String userId, LocalDate date) {
        try {
            String key = uvKey(date);
            redisTemplate.opsForHyperLogLog().add(key, userId);
            redisTemplate.expire(key, Duration.ofDays(35));
        } catch (DataAccessException exception) {
            log.warn("Community feed HLL update failed: {}", exception.toString());
        }
    }

    public long approximateFeedUv(LocalDate date) {
        try {
            Long size = redisTemplate.opsForHyperLogLog().size(uvKey(date));
            return size == null ? 0 : size;
        } catch (DataAccessException exception) {
            log.warn("Community feed HLL read failed: {}", exception.toString());
            return 0;
        }
    }

    public StringRedisTemplate redisTemplate() {
        return redisTemplate;
    }

    public static String geoKey() { return GEO_KEY; }

    private static String uvKey(LocalDate date) {
        return "community:feed:uv:" + DAY_FORMAT.format(date);
    }
}
