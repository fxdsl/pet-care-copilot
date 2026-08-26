package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增会话消息请求，对角色和消息长度进行统一校验。
 */
public record CreateMessageRequest(
        @NotBlank(message = "消息角色不能为空")
        @Pattern(regexp = "USER|ASSISTANT|SYSTEM|TOOL", message = "消息角色无效")
        String role,
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 20000, message = "消息内容不能超过 20000 个字符")
        String content,
        @Size(max = 100, message = "模型名称不能超过 100 个字符")
        String modelName,
        Integer tokenCount
) {
}
