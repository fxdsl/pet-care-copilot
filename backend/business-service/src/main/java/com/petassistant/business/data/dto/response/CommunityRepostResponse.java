package com.petassistant.business.data.dto.response;

/** 转发目标状态和数据库同步后的准确计数。 */
public record CommunityRepostResponse(boolean active, long count) { }
