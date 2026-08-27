package com.petassistant.business.data.dto.internal;

/** 帖子批量计数投影，避免信息流逐帖查询。 */
public record PostCountRow(String postId, long count) { }
