package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SSE 问答请求；requestId 在断线重连时复用，防止同一问题重复保存。 */
public record ChatStreamRequest(
        @NotBlank(message = "流请求编号不能为空")
        @Size(max = 36, message = "流请求编号格式无效")
        String requestId,
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
    /** 复用稳定的非流式业务请求，避免维护两套问答校验和检索逻辑。 */
    public ChatRequest toChatRequest() {
        return new ChatRequest(question, conversationId, petProfileId, petType, category);
    }
}
