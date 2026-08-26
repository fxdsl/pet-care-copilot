package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 普通用户把自己已发布的社区经验申请收录知识库。 */
public record CreateCommunityKnowledgeSubmissionRequest(
        @NotBlank(message = "postId 不能为空") String postId,
        @NotBlank(message = "宠物类型不能为空") @Size(max = 30) String petType,
        @NotBlank(message = "知识分类不能为空") @Size(max = 50) String category,
        @AssertTrue(message = "必须确认拥有内容授权并同意审核后收录") boolean consentGranted
) { }
