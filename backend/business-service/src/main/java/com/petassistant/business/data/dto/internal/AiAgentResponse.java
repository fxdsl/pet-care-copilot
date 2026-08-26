package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI LangGraph Agent 响应，包含答案、来源和脱敏执行轨迹。 */
public record AiAgentResponse(
        String answer,
        @JsonProperty("conversation_id") String conversationId,
        List<AiAgentSource> sources,
        String stage,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("routing_reason") String routingReason,
        @JsonProperty("max_score") Double maxScore,
        @JsonProperty("agent_steps") List<AiAgentStep> agentSteps,
        @JsonProperty("termination_reason") String terminationReason,
        @JsonProperty("tool_call_count") int toolCallCount
) {
}
