package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import com.petassistant.business.data.dto.request.CreateConversationRequest;
import com.petassistant.business.data.dto.request.CreateMessageRequest;
import com.petassistant.business.data.dto.response.ConversationResponse;
import com.petassistant.business.data.dto.response.MessageResponse;
import com.petassistant.business.data.entity.ConversationEntity;
import com.petassistant.business.data.entity.MessageEntity;
import com.petassistant.business.data.mapper.ConversationMapper;
import com.petassistant.business.exception.ConversationNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话业务服务，是 MySQL 事务与 Redis 缓存一致性的边界。
 */
@Service
public class ConversationService {

    private final ConversationMapper mapper;
    private final ConversationContextCacheService contextCacheService;

    /** 注入 MyBatis Mapper 与最近上下文缓存服务。 */
    public ConversationService(ConversationMapper mapper, ConversationContextCacheService contextCacheService) {
        this.mapper = mapper;
        this.contextCacheService = contextCacheService;
    }

    /** 创建会话；暂不写缓存，等模型首次读取最近上下文时再按需缓存。 */
    @Transactional
    public ConversationResponse create(String userId, CreateConversationRequest request) {
        Instant now = Instant.now();
        ConversationEntity entity = new ConversationEntity(
                UUID.randomUUID().toString(), userId, request.title().trim(),
                "ACTIVE", now, now
        );
        mapper.insertConversation(entity);
        return toResponse(entity);
    }

    /**
     * 根据首次问题为当前用户创建会话。标题只保留前 30 个 Unicode 字符，便于会话列表浏览。
     */
    @Transactional
    public ConversationResponse createForQuestion(String userId, String question) {
        String normalized = question.trim();
        int[] codePoints = normalized.codePoints().limit(30).toArray();
        String title = new String(codePoints, 0, codePoints.length);
        if (normalized.codePointCount(0, normalized.length()) > codePoints.length) {
            title += "…";
        }
        return create(userId, new CreateConversationRequest(title));
    }

    /** 查询最近会话并把数据库实体转换为稳定的接口响应。 */
    @Transactional(readOnly = true)
    public List<ConversationResponse> list(String userId, int limit) {
        return mapper.findRecentByUser(userId, clampLimit(limit)).stream()
                .map(ConversationService::toResponse).toList();
    }

    /** 在同一事务中写入消息并更新活动时间，只有提交成功后才使旧上下文失效。 */
    @Transactional
    public MessageResponse addMessage(String userId, String conversationId, CreateMessageRequest request) {
        requireConversation(userId, conversationId);
        MessageEntity entity = new MessageEntity(
                UUID.randomUUID().toString(), conversationId, request.role(), request.content().trim(),
                blankToNull(request.modelName()), request.tokenCount(), Instant.now()
        );
        mapper.insertMessage(entity);
        mapper.touchConversation(conversationId, entity.createdAt());
        contextCacheService.evictAfterCommit(conversationId);
        return toResponse(entity);
    }

    /** 查询会话全部消息；先确认会话存在以返回明确的 404。 */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String userId, String conversationId) {
        requireConversation(userId, conversationId);
        return mapper.findMessages(conversationId).stream().map(ConversationService::toResponse).toList();
    }

    /**
     * 读取最近的用户和助手消息作为模型上下文；数量限制在 1 到 20，控制 Prompt 长度。
     */
    @Transactional(readOnly = true)
    public List<ConversationContextMessage> getRecentMessagesForContext(
            String userId,
            String conversationId,
            int limit
    ) {
        //选取最近 12 条消息作为上下文，控制 Prompt 长度
        int safeLimit = Math.min(Math.max(limit, 1), 12);
        //使用redis作为缓存，减少数据库查询次数。
        // UUID 即使被猜到也不能直接读取缓存，必须先由 MySQL 确认当前用户拥有该会话。
        requireConversation(userId, conversationId);
        var cached = contextCacheService.get(conversationId);
        if (cached.isPresent()) {
            return tail(cached.get(), safeLimit);
        }
        //如果缓存中没有，从数据库查询。
        List<ConversationContextMessage> context = mapper.findRecentMessages(conversationId, safeLimit).stream()
                .map(message -> new ConversationContextMessage(message.role(), message.content()))
                .toList();
        //将缓存结果存入redis。
        contextCacheService.put(conversationId, context);
        return context;
    }

    /** 查询会话实体，不存在时抛出业务异常。 */
    private ConversationEntity requireConversation(String userId, String id) {
        ConversationEntity entity = mapper.findByIdAndUser(id, userId);
        if (entity == null) {
            throw new ConversationNotFoundException(id);
        }
        return entity;
    }

    /** 将会话实体转换为外部响应。 */
    private static ConversationResponse toResponse(ConversationEntity entity) {
        return new ConversationResponse(
                entity.id(), entity.userId(), entity.title(), entity.status(), entity.createdAt(), entity.updatedAt()
        );
    }

    /** 将消息实体转换为外部响应。 */
    private static MessageResponse toResponse(MessageEntity entity) {
        return new MessageResponse(
                entity.id(), entity.conversationId(), entity.role(), entity.content(),
                entity.modelName(), entity.tokenCount(), entity.createdAt()
        );
    }

    /** 限制列表数量，避免接口一次加载过多会话。 */
    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    /** 把空白可选字段统一转换为 null。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 从缓存快照尾部取指定条数，同时保持时间正序。 */
    private static List<ConversationContextMessage> tail(
            List<ConversationContextMessage> messages,
            int limit
    ) {
        return messages.subList(Math.max(0, messages.size() - limit), messages.size());
    }
}
