package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.dto.internal.PostCountRow;
import com.petassistant.business.data.dto.internal.PostValueRow;
import com.petassistant.business.data.entity.CommunityRelationControlEntity;
import com.petassistant.business.data.entity.CommunityRepostEntity;
import com.petassistant.business.data.entity.RecommendationFeedbackEntity;
import org.apache.ibatis.annotations.Param;

/** 第十三周转发、关系治理和推荐反馈 Mapper。 */
public interface CommunityGovernanceMapper {

    int upsertRepost(CommunityRepostEntity repost);
    CommunityRepostEntity findRepost(@Param("postId") String postId, @Param("userId") String userId);
    int synchronizeRepostCount(@Param("postId") String postId);
    long countReposts(@Param("postId") String postId);
    List<String> findRepostedPostIds(@Param("userId") String userId, @Param("postIds") List<String> postIds);
    List<PostCountRow> findRepostCounts(@Param("postIds") List<String> postIds);

    int upsertRelation(CommunityRelationControlEntity relation);
    CommunityRelationControlEntity findRelation(
            @Param("actorUserId") String actorUserId,
            @Param("targetUserId") String targetUserId,
            @Param("relationType") String relationType
    );
    boolean existsBlockEitherDirection(@Param("firstUserId") String firstUserId, @Param("secondUserId") String secondUserId);
    List<String> findExcludedAuthorIds(@Param("viewerId") String viewerId);

    int upsertFeedback(RecommendationFeedbackEntity feedback);
    RecommendationFeedbackEntity findFeedback(@Param("userId") String userId, @Param("postId") String postId);
    List<String> findNotInterestedPostIds(@Param("userId") String userId, @Param("postIds") List<String> postIds);
    List<String> findPreferredTopicIds(@Param("userId") String userId, @Param("limit") int limit);
    List<String> findPetTypes(@Param("userId") String userId);
    List<PostValueRow> findPostPetTypes(@Param("postIds") List<String> postIds);

    CommunityRepostEntity findRepostById(@Param("id") String id);
    CommunityRelationControlEntity findRelationById(@Param("id") String id);
    RecommendationFeedbackEntity findFeedbackById(@Param("id") String id);
    int claimEvent(@Param("eventId") String eventId, @Param("consumerName") String consumerName, @Param("processedAt") Instant processedAt);
}
