package com.petassistant.business.data.dto.response;

/** 关注切换后的最终状态与被关注者粉丝数。 */
public record CommunityFollowResponse(boolean following, long followerCount) { }
