package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。用户编号只取自 JWT，不允许浏览器自行指定。
 */
public record CreateConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 200, message = "会话标题不能超过 200 个字符")
        String title
) {
}
