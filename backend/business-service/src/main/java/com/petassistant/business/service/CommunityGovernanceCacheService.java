package com.petassistant.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.petassistant.business.data.entity.CommunityRelationControlEntity;
import com.petassistant.business.data.entity.CommunityRepostEntity;
import com.petassistant.business.data.entity.RecommendationFeedbackEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 第十三周 Redis 副本：治理 Set、推荐 ZSet/Hash 和可重放反馈 Stream。 */
@Service
public class CommunityGovernanceCacheService {

    private static final Logger log = LoggerFactory.getLogger(CommunityGovernanceCacheService.class);
    private static final Duration RELATION_TTL = Duration.ofDays(30);
    private static final Duration RECOMMENDATION_TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redisTemplate;

    public CommunityGovernanceCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 同步屏蔽/拉黑 Set；MySQL 仍是最终权限判断依据。 */
    public void synchronizeRelation(CommunityRelationControlEntity relation) {
        if (relation == null) return;
        String suffix = "BLOCK".equals(relation.relationType()) ? "blocked" : "muted";
        String key = "community:user:" + relation.actorUserId() + ":" + suffix;
        try {
            if (relation.active()) redisTemplate.opsForSet().add(key, relation.targetUserId());
            else redisTemplate.opsForSet().remove(key, relation.targetUserId());
            redisTemplate.expire(key, RELATION_TTL);
            invalidateRecommendations(relation.actorUserId());
            if ("BLOCK".equals(relation.relationType())) invalidateRecommendations(relation.targetUserId());
        } catch (DataAccessException error) {
            log.warn("Governance relation cache unavailable: {}", error.toString());
        }
    }

    public void synchronizeRepost(CommunityRepostEntity repost) {
        if (repost == null) return;
        String key = "community:user:" + repost.userId() + ":reposts";
        try {
            if (repost.active()) redisTemplate.opsForSet().add(key, repost.postId());
            else redisTemplate.opsForSet().remove(key, repost.postId());
            redisTemplate.expire(key, RELATION_TTL);
        } catch (DataAccessException error) {
            log.warn("Repost cache unavailable: {}", error.toString());
        }
    }

    /** 反馈同时写 Set 与 Stream；Stream 供后续离线排序器按消费组重放。 */
    public void synchronizeFeedback(RecommendationFeedbackEntity feedback) {
        if (feedback == null) return;
        String key = "recommendation:user:" + feedback.userId() + ":not-interested";
        try {
            if (feedback.active()) redisTemplate.opsForSet().add(key, feedback.postId());
            else redisTemplate.opsForSet().remove(key, feedback.postId());
            redisTemplate.expire(key, RELATION_TTL);
            redisTemplate.opsForStream().add(StreamRecords.newRecord()
                    .in("recommendation:feedback:stream")
                    .ofMap(Map.of(
                            "feedbackId", feedback.id(), "userId", feedback.userId(),
                            "postId", feedback.postId(), "active", Boolean.toString(feedback.active()),
                            "occurredAt", Instant.now().toString()
                    )));
            redisTemplate.opsForStream().trim("recommendation:feedback:stream", 10_000, true);
            invalidateRecommendations(feedback.userId());
        } catch (DataAccessException error) {
            log.warn("Recommendation feedback cache unavailable: {}", error.toString());
        }
    }

    /** ZSet 保存候选分，Hash 保存同批解释；二者均可由 MySQL 重建。 */
    public void cacheRecommendations(String userId, List<ScoredRecommendation> items) {
        String rankingKey = "recommendation:user:" + userId + ":candidates";
        String explanationKey = "recommendation:user:" + userId + ":explanations";
        try {
            redisTemplate.delete(List.of(rankingKey, explanationKey));
            for (ScoredRecommendation item : items) {
                redisTemplate.opsForZSet().add(rankingKey, item.postId(), item.score());
                redisTemplate.opsForHash().put(explanationKey, item.postId(), item.reason());
            }
            redisTemplate.expire(rankingKey, RECOMMENDATION_TTL);
            redisTemplate.expire(explanationKey, RECOMMENDATION_TTL);
        } catch (DataAccessException error) {
            log.warn("Recommendation cache write skipped: {}", error.toString());
        }
    }

    public void invalidateRecommendations(String userId) {
        try {
            redisTemplate.delete(List.of(
                    "recommendation:user:" + userId + ":candidates",
                    "recommendation:user:" + userId + ":explanations"
            ));
        } catch (DataAccessException error) {
            log.debug("Recommendation cache eviction skipped: {}", error.toString());
        }
    }

    public record ScoredRecommendation(String postId, double score, String reason) { }
}
