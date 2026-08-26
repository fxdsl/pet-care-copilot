package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发表评论；parentId 为空表示一级评论，否则表示回复。 */
public record CreateCommunityCommentRequest(
        String parentId,
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 2000, message = "评论内容不能超过 2000 个字符")
        String content
) { }
