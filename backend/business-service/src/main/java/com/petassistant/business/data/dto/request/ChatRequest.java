package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 宠物知识问答请求。
 * 宠物类型与知识分类为可选过滤条件，未填写时检索全部已向量化资料。
 */
public record ChatRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题不能超过 2000 个字符")
        String question,
        @Size(max = 36, message = "会话编号格式无效")
        String conversationId,
        @Size(max = 36, message = "宠物档案编号格式无效")
        String petProfileId,
        @Size(max = 30, message = "宠物类型不能超过 30 个字符")
        String petType,
        @Size(max = 50, message = "知识分类不能超过 50 个字符")
        String category
) {
}
