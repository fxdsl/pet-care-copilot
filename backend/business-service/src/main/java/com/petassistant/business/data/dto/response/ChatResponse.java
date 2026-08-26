package com.petassistant.business.data.dto.response;

import java.util.List;

/**
 * Agent 问答响应，包含答案、真实来源和可展示的脱敏执行轨迹。
 */
public record ChatResponse(
        String answer,
        String conversationId,
        List<SourceReference> sources,
        String stage,
        String modelName,
        String routingReason,
        Double maxScore,
        List<AgentStepResponse> agentSteps,
        String terminationReason,
        int toolCallCount
) {
    /**
     * 展示给前端的知识来源，分数为当前检索排序相关度。
     */
    public record SourceReference(
            String title,
            String url,
            String chunkId,
            double score,
            String fileName,
            Integer pageStart,
            Integer pageEnd
    ) {
    }

    /** 前端可展示的节点摘要；不包含模型隐藏思维过程或原始工具正文。 */
    public record AgentStepResponse(
            int sequence,
            String node,
            String action,
            String toolName,
            String status,
            String summary
    ) {
    }
}
