package com.petassistant.business.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.client.AiServiceClient;
import com.petassistant.business.config.AgentProperties;
import com.petassistant.business.data.dto.internal.AiConversationMessage;
import com.petassistant.business.data.dto.internal.AiPetProfile;
import com.petassistant.business.data.dto.internal.AiAgentCandidate;
import com.petassistant.business.data.dto.internal.AiAgentRequest;
import com.petassistant.business.data.dto.internal.AiAgentResponse;
import com.petassistant.business.data.dto.internal.AiStreamEvent;
import com.petassistant.business.data.dto.internal.AgentCandidateRow;
import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import com.petassistant.business.data.dto.request.ChatRequest;
import com.petassistant.business.data.dto.request.KnowledgeTestRequest;
import com.petassistant.business.data.dto.request.CreateMessageRequest;
import com.petassistant.business.data.dto.response.ChatResponse;
import com.petassistant.business.data.dto.response.ConversationResponse;
import com.petassistant.business.data.entity.PetProfileEntity;
import com.petassistant.business.data.mapper.KnowledgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第六周问答编排服务。
 * MySQL/MyBatis 提供候选知识，Redis 缓存最近上下文，FastAPI 运行受控 LangGraph Agent。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final TypeReference<List<Double>> EMBEDDING_TYPE = new TypeReference<>() { };

    private final KnowledgeMapper knowledgeMapper;
    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final ConversationService conversationService;
    private final ConversationContextCacheService contextCacheService;
    private final PetProfileService petProfileService;
    private final PlatformMetricsService metrics;

    /** 注入候选数据访问、FastAPI 客户端、JSON 工具和 RAG 参数。 */
    public ChatService(
            KnowledgeMapper knowledgeMapper,
            AiServiceClient aiServiceClient,
            ObjectMapper objectMapper,
            AgentProperties agentProperties,
            ConversationService conversationService,
            ConversationContextCacheService contextCacheService,
            PetProfileService petProfileService,
            PlatformMetricsService metrics
    ) {
        this.knowledgeMapper = knowledgeMapper;
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.conversationService = conversationService;
        this.contextCacheService = contextCacheService;
        this.petProfileService = petProfileService;
        this.metrics = metrics;
    }

    /**
     * 执行第六周完整问答事务：解析档案、读取缓存上下文、调用 Agent 并保存双向消息。
     * 任一环节失败时数据库事务整体回滚，避免只保存半轮对话。
     */
    @Transactional
    public ChatResponse answer(String userId, ChatRequest request) {
        return answerInternal(userId, request, null);
    }

    /** 第十周流式入口复用同一事务和持久化逻辑，只替换 FastAPI 传输协议。 */
    @Transactional
    public ChatResponse answerStreaming(
            String userId,
            ChatRequest request,
            Consumer<AiStreamEvent> listener
    ) {
        return answerInternal(userId, request, listener);
    }

    private ChatResponse answerInternal(
            String userId,
            ChatRequest request,
            Consumer<AiStreamEvent> streamListener
    ) {

        // ① 解析用户宠物档案：根据前端传入的档案ID加载宠物信息（品种、年龄等），支持个性化问答
        PetProfileEntity profile = resolveProfile(userId, request.petProfileId());

        // ② 获取会话ID：将空白字符串转为null，便于后续判断是否为新会话
        String conversationId = blankToNull(request.conversationId());
        // 声明历史消息列表，用于存储多轮对话上下文
        List<ConversationContextMessage> history;

        // ③ 会话管理：判断是新会话还是续接旧会话
        if (conversationId == null) {
            // 首次对话：根据用户问题创建新会话，获取系统生成的会话ID
            ConversationResponse created = conversationService.createForQuestion(userId, request.question());
            conversationId = created.id();
            history = List.of();  // 新会话无历史消息
        } else {
            // 续接旧会话：读取最近12条消息作为上下文，同时验证会话有效性（无效ID返回404）
            history = conversationService.getRecentMessagesForContext(userId, conversationId, 12);
        }

        MDC.put("conversationId", conversationId);
        try {
        // ④ 确定有效的宠物类型：有档案时优先使用档案数据（更准确），无档案时使用请求参数（兜底）
        String effectivePetType = profile == null ? request.petType() : profile.petType();

        // ⑤ 知识库粗筛：从MySQL查询相关的知识分块（按宠物类型和分类过滤，限制候选数量）
        List<AgentCandidateRow> rows = knowledgeMapper.findAgentCandidates(
                normalizedUpper(effectivePetType),   // 宠物类型转大写，匹配数据库枚举
                normalizedUpper(request.category()),   // 分类转大写
                clamp(agentProperties.candidateLimit(), 1, 2000)  // 候选数量限制在安全范围
        );

        // ⑥ 反序列化向量数据：将数据库中的JSON格式向量转换为Java对象（单条损坏时跳过）
        List<AiAgentCandidate> candidates = deserializeCandidates(rows);

        // ⑦ 组装 AI 请求：只传授权数据，Agent 不能自行读取任意会话、档案或数据库记录。
        AiAgentRequest aiRequest = new AiAgentRequest(
                request.question().trim(),           // 用户问题（去首尾空格）
                conversationId,                      // 会话ID（用于多轮上下文）
                toAiHistory(history),               // 转换历史消息格式
                toAiProfile(profile),              // 转换宠物档案格式
                candidates,                          // 候选知识分块列表
                clamp(agentProperties.topK(), 1, 3),  // 单次工具最终最多返回3条，降低上下文占用
                Math.max(-1.0, Math.min(agentProperties.minScore(), 1.0))  // 相似度阈值（-1表示不过滤）
        );

        // ⑧ 调用 AI 服务：LangGraph 决定是否使用知识或当前档案工具并生成回答。
        AiAgentResponse aiResponse = streamListener == null
                ? aiServiceClient.answer(aiRequest)
                : aiServiceClient.answerStreaming(aiRequest, streamListener);
        metrics.recordAgentUsage(request.question(), aiResponse.answer(), aiResponse.toolCallCount());

        // ⑨ 事务性保存消息：在数据库事务中保存本轮对话的USER消息（问题）
        conversationService.addMessage(
                userId,
                conversationId,
                new CreateMessageRequest("USER", request.question().trim(), null, null)
        );

        // ⑩ 事务性保存消息：在数据库事务中保存本轮对话的ASSISTANT消息（AI回答）
        conversationService.addMessage(
                userId,
                conversationId,
                new CreateMessageRequest("ASSISTANT", aiResponse.answer(), aiResponse.modelName(), null)
        );

        // ⑪ 数据库提交后刷新最近 12 条上下文；前面注册的失效回调会先删除旧值。
        List<ConversationContextMessage> updatedContext = new ArrayList<>(history);
        updatedContext.add(new ConversationContextMessage("USER", request.question().trim()));
        updatedContext.add(new ConversationContextMessage("ASSISTANT", aiResponse.answer()));
        contextCacheService.putAfterCommit(conversationId, updatedContext);

        // ⑫ 格式转换：将AI返回的内部DTO转换为前端可用的外部DTO。
        List<ChatResponse.SourceReference> sources = aiResponse.sources() == null
                ? List.of()  // 无引用来源时返回空列表
                : aiResponse.sources().stream()
                  .map(source -> new ChatResponse.SourceReference(
                          source.title(),    // 文章标题
                          source.url(),      // 来源链接
                          source.chunkId(),  // 分块ID（定位具体段落）
                          source.score(),    // 相似度得分（可信度指标）
                          source.fileName(),
                          source.pageStart(),
                          source.pageEnd()
                  ))
                  .toList();

        // ⑬ 仅映射经过 FastAPI 脱敏的动作摘要，不向网页暴露模型思维过程或完整工具观察。
        List<ChatResponse.AgentStepResponse> agentSteps = aiResponse.agentSteps() == null
                ? List.of()
                : aiResponse.agentSteps().stream()
                  .map(step -> new ChatResponse.AgentStepResponse(
                          step.sequence(), step.node(), step.action(), step.toolName(),
                          step.status(), step.summary()
                  ))
                  .toList();

        // ⑭ 构建最终响应：终止原因和工具次数让前端可以解释正常结束或安全熔断。
        return new ChatResponse(
                aiResponse.answer(),      // AI生成的回答文本
                conversationId,           // 会话ID（前端用于后续请求）
                sources,                  // 参考资料来源列表
                aiResponse.stage(),       // 处理阶段标识
                aiResponse.modelName(),   // 实际模型名称
                aiResponse.routingReason(),
                aiResponse.maxScore(),
                agentSteps,
                aiResponse.terminationReason(),
                aiResponse.toolCallCount()
        );
        } finally {
            MDC.remove("conversationId");
        }
    }

    /**
     * 管理员专用 RAG 验证：复用真实候选检索和 Agent，但不读取宠物档案、上下文或保存会话消息。
     */
    @Transactional(readOnly = true)
    public ChatResponse testKnowledge(KnowledgeTestRequest request) {
        List<AgentCandidateRow> rows = knowledgeMapper.findAgentCandidates(
                normalizedUpper(request.petType()), normalizedUpper(request.category()),
                clamp(agentProperties.candidateLimit(), 1, 2000)
        );
        AiAgentResponse aiResponse = aiServiceClient.answer(new AiAgentRequest(
                request.question().trim(), "admin-knowledge-test", List.of(), null,
                deserializeCandidates(rows), clamp(agentProperties.topK(), 1, 3),
                Math.max(-1.0, Math.min(agentProperties.minScore(), 1.0))
        ));
        List<ChatResponse.SourceReference> sources = aiResponse.sources() == null
                ? List.of()
                : aiResponse.sources().stream().map(source -> new ChatResponse.SourceReference(
                        source.title(), source.url(), source.chunkId(), source.score(),
                        source.fileName(), source.pageStart(), source.pageEnd()
                )).toList();
        List<ChatResponse.AgentStepResponse> steps = aiResponse.agentSteps() == null
                ? List.of()
                : aiResponse.agentSteps().stream().map(step -> new ChatResponse.AgentStepResponse(
                        step.sequence(), step.node(), step.action(), step.toolName(), step.status(), step.summary()
                )).toList();
        return new ChatResponse(
                aiResponse.answer(), null, sources, aiResponse.stage(), aiResponse.modelName(),
                aiResponse.routingReason(), aiResponse.maxScore(), steps,
                aiResponse.terminationReason(), aiResponse.toolCallCount()
        );
    }

    /** 仅在前端选择档案时读取实体；未选择时保留通用问答。 */
    private PetProfileEntity resolveProfile(String userId, String profileId) {
        String normalizedId = blankToNull(profileId);
        return normalizedId == null ? null : petProfileService.requireEntity(userId, normalizedId);
    }

    /** 把数据库消息缩减为 FastAPI 所需的角色和正文。 */
    private static List<AiConversationMessage> toAiHistory(List<ConversationContextMessage> history) {
        return history.stream()
                .filter(message -> "USER".equals(message.role()) || "ASSISTANT".equals(message.role()))
                .map(message -> new AiConversationMessage(message.role(), message.content()))
                .toList();
    }

    /** 把数据库档案转换为不含内部字段的模型上下文。 */
    private static AiPetProfile toAiProfile(PetProfileEntity profile) {
        if (profile == null) {
            return null;
        }
        return new AiPetProfile(
                profile.name(), profile.petType(), profile.breed(), profile.ageMonths(),
                profile.weightKg(), profile.notes()
        );
    }

    /**
     * 将 MySQL JSON 向量转换为 FastAPI 请求对象；单条损坏数据会被跳过，不中断全部问答。
     */
    private List<AiAgentCandidate> deserializeCandidates(List<AgentCandidateRow> rows) {
        List<AiAgentCandidate> candidates = new ArrayList<>();
        for (AgentCandidateRow row : rows) {
            try {
                List<Double> embedding = objectMapper.readValue(row.embeddingJson(), EMBEDDING_TYPE);
                candidates.add(new AiAgentCandidate(
                        row.chunkId(), row.documentId(), row.title(), row.sourceName(), row.sourceUrl(),
                        row.chunkIndex(), row.content(), embedding, row.embeddingModel(), row.fileName(),
                        row.pageStart(), row.pageEnd()
                ));
            } catch (Exception error) {
                log.warn("Skip invalid embedding for chunk {}: {}", row.chunkId(), error.toString());
            }
        }
        return candidates;
    }

    /** 将可选过滤值统一为数据库中使用的大写枚举形式。 */
    private static String normalizedUpper(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    /** 将空白字符串转换为 null，避免动态 SQL 产生无意义条件。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 将可配置整数约束在安全范围内。 */
    private static int clamp(int value, int minimum, int maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }
}
