package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.Size;

/** 引用转发可附带最多 500 字说明，空内容表示普通转发。 */
public record CreateCommunityRepostRequest(@Size(max = 500) String quoteContent) { }
