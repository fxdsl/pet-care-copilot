package com.petassistant.business.data.entity;

import java.time.Instant;

/** 推荐反馈事实；当前支持 NOT_INTERESTED，撤销时保留同一主记录。 */
public record RecommendationFeedbackEntity(
        String id, String userId, String postId, String feedbackType,
        boolean active, Instant createdAt, Instant updatedAt
) { }
