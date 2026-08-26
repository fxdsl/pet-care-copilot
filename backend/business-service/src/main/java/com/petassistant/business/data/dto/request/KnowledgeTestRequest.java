package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员知识库检索/回答测试，不创建普通用户会话。 */
public record KnowledgeTestRequest(
        @NotBlank(message = "测试问题不能为空")
        @Size(max = 2000, message = "测试问题不能超过 2000 个字符")
        String question,
        @Size(max = 30, message = "宠物类型不能超过 30 个字符")
        String petType,
        @Size(max = 50, message = "知识分类不能超过 50 个字符")
        String category
) { }
