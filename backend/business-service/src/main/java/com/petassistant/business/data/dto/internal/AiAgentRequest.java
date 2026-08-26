package com.petassistant.business.data.dto.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Spring Boot 发送给 LangGraph Agent 的完整请求。 */
public record AiAgentRequest(
        String question,
        @JsonProperty("conversation_id") String conversationId,
        List<AiConversationMessage> history,
        @JsonProperty("pet_profile") AiPetProfile petProfile,
        List<AiAgentCandidate> candidates,
        @JsonProperty("top_k") int topK,
        @JsonProperty("min_score") double minScore
) {
}
