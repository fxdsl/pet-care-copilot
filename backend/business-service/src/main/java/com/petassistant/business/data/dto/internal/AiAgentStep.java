package com.petassistant.business.data.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 可展示的 Agent 执行摘要。
 * summary 只包含动作结果，不保存或传递模型隐藏思维过程。
 */
public record AiAgentStep(
        int sequence,
        String node,
        String action,
        @JsonProperty("tool_name") String toolName,
        String status,
        String summary
) {
}
