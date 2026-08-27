package com.petassistant.business.data.dto.response;

/** 屏蔽/拉黑操作返回最终关系状态。 */
public record CommunityRelationControlResponse(String userId, String relationType, boolean active) { }
