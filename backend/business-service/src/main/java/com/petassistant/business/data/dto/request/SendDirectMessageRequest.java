package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发送私信请求；客户端消息号让断线重试保持幂等。 */
public record SendDirectMessageRequest(
        @NotBlank(message = "接收者不能为空")
        @Size(max = 36, message = "接收者编号格式无效")
        String recipientId,
        @NotBlank(message = "客户端消息号不能为空")
        @Size(max = 36, message = "客户端消息号格式无效")
        String clientMessageId,
        @NotBlank(message = "私信内容不能为空")
        @Size(max = 2000, message = "私信不能超过 2000 个字符")
        String content
) {
}
