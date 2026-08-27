package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.AiServiceClient;
import com.petassistant.business.config.AgentProperties;
import com.petassistant.business.data.dto.internal.AiAgentRequest;
import com.petassistant.business.data.dto.internal.AiAgentResponse;
import com.petassistant.business.data.dto.internal.AiAgentSource;
import com.petassistant.business.data.dto.internal.AiAgentStep;
import com.petassistant.business.data.dto.internal.AgentCandidateRow;
import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import com.petassistant.business.data.dto.request.ChatRequest;
import com.petassistant.business.data.dto.response.ChatResponse;
import com.petassistant.business.data.dto.response.ConversationResponse;
import com.petassistant.business.data.entity.PetProfileEntity;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** ChatService 的候选读取、向量反序列化和响应映射测试。 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private KnowledgeMapper knowledgeMapper;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private ConversationService conversationService;

    @Mock
    private ConversationContextCacheService contextCacheService;

    @Mock
    private PetProfileService petProfileService;

    @Mock
    private PlatformMetricsService metrics;

    private ChatService service;

    /** 使用真实 ObjectMapper 构造被测服务，覆盖 MySQL JSON 向量转换。 */
    @BeforeEach
    void setUp() {
        service = new ChatService(
                knowledgeMapper,
                aiServiceClient,
                new ObjectMapper(),
                new AgentProperties(500, 3, 0.35),
                conversationService,
                contextCacheService,
                petProfileService,
                metrics
        );
    }

    /** 已向量化候选应传给 FastAPI，并将来源映射为前端响应。 */
    @Test
    void shouldBuildAgentRequestAndMapSourcesAndSteps() {
        Instant now = Instant.now();
        when(conversationService.createForQuestion("user-1", "幼猫喂几次？")).thenReturn(
                new ConversationResponse("conversation-1", "user-1", "幼猫喂几次？", "ACTIVE", now, now)
        );
        when(knowledgeMapper.findAgentCandidates(null, null, 500)).thenReturn(List.of(
                new AgentCandidateRow(
                        "chunk-1", "document-1", "幼猫基础喂养", "测试资料", null,
                        "guide.pdf", "CAT", "FEEDING", 0, "幼猫需要少量多餐。", "[0.5,0.5]",
                        "BAAI/bge-small-zh-v1.5", 2, 2
                )
        ));
        when(aiServiceClient.answer(any())).thenReturn(new AiAgentResponse(
                "根据知识库：幼猫需要少量多餐。",
                null,
                List.of(new AiAgentSource("幼猫基础喂养", null, "chunk-1", 0.61, "guide.pdf", 2, 2)),
                "week-6-agent-rag",
                "qwen3.7-plus",
                "AGENT_FINAL_WITH_KNOWLEDGE",
                0.61,
                List.of(new AiAgentStep(1, "tool", "EXECUTE_TOOL", "search_pet_knowledge", "SUCCESS", "命中 1 条资料。")),
                "COMPLETED",
                1
        ));

        ChatResponse result = service.answer(
                "user-1",
                new ChatRequest("幼猫喂几次？", null, null, null, null)
        );

        assertThat(result.stage()).isEqualTo("week-6-agent-rag");
        assertThat(result.conversationId()).isEqualTo("conversation-1");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(result.sources().get(0).pageStart()).isEqualTo(2);
        assertThat(result.agentSteps()).hasSize(1);
        assertThat(result.terminationReason()).isEqualTo("COMPLETED");
        assertThat(result.toolCallCount()).isEqualTo(1);
        ArgumentCaptor<AiAgentRequest> requestCaptor =
                ArgumentCaptor.forClass(AiAgentRequest.class);
        verify(aiServiceClient).answer(requestCaptor.capture());
        assertThat(requestCaptor.getValue().conversationId()).isEqualTo("conversation-1");
        verify(conversationService, times(2)).addMessage(any(), any(), any());
    }

    /** 已有会话和宠物档案应一起传给 FastAPI，并使用档案类型过滤知识。 */
    @Test
    void shouldForwardHistoryAndPetProfileForFollowUp() {
        Instant now = Instant.now();
        when(petProfileService.requireEntity("user-1", "profile-1")).thenReturn(new PetProfileEntity(
                "profile-1", "user-1", "团子", "CAT", "中华田园猫", 4,
                new java.math.BigDecimal("1.80"), null, now, now
        ));
        when(conversationService.getRecentMessagesForContext("user-1", "conversation-1", 12)).thenReturn(List.of(
                new ConversationContextMessage("USER", "幼猫一天喂几次？"),
                new ConversationContextMessage("ASSISTANT", "建议少量多餐。")
        ));
        when(knowledgeMapper.findAgentCandidates("CAT", "FEEDING", 500)).thenReturn(List.of());
        when(aiServiceClient.answer(any())).thenReturn(new AiAgentResponse(
                "每次喂食量还应结合体重。", "conversation-1", List.of(),
                "week-6-agent-general", "qwen3.7-plus", "AGENT_FINAL_GENERAL", 0.72,
                List.of(), "COMPLETED", 0
        ));

        ChatResponse result = service.answer(
                "user-1",
                new ChatRequest("那每次喂多少？", "conversation-1", "profile-1", null, "FEEDING")
        );

        ArgumentCaptor<AiAgentRequest> requestCaptor =
                ArgumentCaptor.forClass(AiAgentRequest.class);
        verify(aiServiceClient).answer(requestCaptor.capture());
        assertThat(requestCaptor.getValue().history()).hasSize(2);
        assertThat(requestCaptor.getValue().petProfile().name()).isEqualTo("团子");
        assertThat(result.conversationId()).isEqualTo("conversation-1");
        verify(contextCacheService).putAfterCommit(any(), any());
    }
}
