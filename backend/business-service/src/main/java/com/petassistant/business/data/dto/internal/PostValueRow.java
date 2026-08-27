package com.petassistant.business.data.dto.internal;

/** 帖子 ID 到单值属性的批量投影。 */
public record PostValueRow(String postId, String value) { }
