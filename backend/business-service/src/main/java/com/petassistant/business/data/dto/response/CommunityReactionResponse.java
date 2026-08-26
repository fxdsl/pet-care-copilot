package com.petassistant.business.data.dto.response;

/** 点赞或收藏切换后的最终状态与数据库计数。 */
public record CommunityReactionResponse(boolean active, long count) { }
