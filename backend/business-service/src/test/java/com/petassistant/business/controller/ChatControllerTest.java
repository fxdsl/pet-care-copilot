package com.petassistant.business.controller;

import java.util.List;

import com.petassistant.business.data.dto.response.ChatResponse;
import com.petassistant.business.security.JwtTokenService;
import com.petassistant.business.security.JsonSecurityErrorWriter;
import com.petassistant.business.service.ChatService;
import com.petassistant.business.service.ChatStreamingService;
import com.petassistant.business.service.PrincipalSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ChatController 的请求校验和响应契约测试。 */
@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private ChatStreamingService chatStreamingService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JsonSecurityErrorWriter jsonSecurityErrorWriter;

    @MockitoBean
    private PrincipalSecurityService principalSecurityService;

    /** 合法问题应返回第六周 Agent 答案、来源和脱敏执行轨迹。 */
    @Test
    void shouldReturnRagAnswerForValidQuestion() throws Exception {
        when(chatService.answer(eq("user-1"), any())).thenReturn(new ChatResponse(
                "根据知识库：幼猫需要少量多餐。",
                "demo",
                List.of(new ChatResponse.SourceReference(
                        "幼猫基础喂养", null, "chunk-1", 0.62, "guide.pdf", 2, 2
                )),
                "week-6-agent-rag",
                "qwen3.7-plus",
                "AGENT_FINAL_WITH_KNOWLEDGE",
                0.62,
                List.of(new ChatResponse.AgentStepResponse(
                        1, "tool", "EXECUTE_TOOL", "search_pet_knowledge", "SUCCESS", "命中 1 条资料。"
                )),
                "COMPLETED",
                1
        ));

        mockMvc.perform(post("/api/v1/chat/preview")
                        .principal(() -> "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"幼猫一天应该喂几次？","conversationId":"demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("week-6-agent-rag"))
                .andExpect(jsonPath("$.conversationId").value("demo"))
                .andExpect(jsonPath("$.sources[0].chunkId").value("chunk-1"))
                .andExpect(jsonPath("$.sources[0].pageStart").value(2))
                .andExpect(jsonPath("$.routingReason").value("AGENT_FINAL_WITH_KNOWLEDGE"))
                .andExpect(jsonPath("$.agentSteps[0].toolName").value("search_pet_knowledge"))
                .andExpect(jsonPath("$.terminationReason").value("COMPLETED"))
                .andExpect(jsonPath("$.toolCallCount").value(1));
    }

    /** 空白问题应在 Controller 参数校验阶段返回 400。 */
    @Test
    void shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/chat/preview")
                        .principal(() -> "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
