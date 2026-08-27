package com.petassistant.business.data.dto.response;

/** 不感兴趣反馈的目标状态响应。 */
public record RecommendationFeedbackResponse(String postId, boolean active) { }
