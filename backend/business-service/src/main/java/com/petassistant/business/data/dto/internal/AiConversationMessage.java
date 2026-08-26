package com.petassistant.business.data.dto.internal;

/**
 * 发送给 FastAPI 的一条历史对话，只允许 USER 与 ASSISTANT 消息参与模型上下文。
 */
public record AiConversationMessage(
        String role,
        String content
) {
}
