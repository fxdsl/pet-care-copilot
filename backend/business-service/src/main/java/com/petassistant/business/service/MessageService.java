package com.petassistant.business.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.petassistant.business.data.dto.internal.DirectConversationView;
import com.petassistant.business.data.dto.internal.DirectMessageView;
import com.petassistant.business.data.dto.internal.NotificationView;
import com.petassistant.business.data.dto.request.SendDirectMessageRequest;
import com.petassistant.business.data.dto.response.DirectConversationPageResponse;
import com.petassistant.business.data.dto.response.DirectConversationResponse;
import com.petassistant.business.data.dto.response.DirectMessagePageResponse;
import com.petassistant.business.data.dto.response.DirectMessageResponse;
import com.petassistant.business.data.dto.response.MessageUnreadResponse;
import com.petassistant.business.data.dto.response.NotificationPageResponse;
import com.petassistant.business.data.dto.response.NotificationResponse;
import com.petassistant.business.data.entity.DirectConversationEntity;
import com.petassistant.business.data.entity.DirectMessageEntity;
import com.petassistant.business.data.entity.NotificationEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.MessageMapper;
import com.petassistant.business.data.mapper.CommunityGovernanceMapper;
import com.petassistant.business.data.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 第十周通知与私信服务；MySQL 是事实源，Redis Hash 仅缓存可重建未读计数。 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final Set<String> NOTIFICATION_TYPES = Set.of(
            "COMMENT", "LIKE", "FOLLOW", "MODERATION", "SYSTEM"
    );

    private final MessageMapper mapper;
    private final CommunityGovernanceMapper governanceMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final RealtimeEventService realtimeEventService;

    public MessageService(
            MessageMapper mapper,
            CommunityGovernanceMapper governanceMapper,
            UserMapper userMapper,
            StringRedisTemplate redisTemplate,
            RealtimeEventService realtimeEventService
    ) {
        this.mapper = mapper;
        this.governanceMapper = governanceMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 在调用方业务事务内写通知。dedupeKey 必须稳定，重复 RabbitMQ/HTTP 事件不会重复计数。
     */
    public void createNotification(
            String recipientId,
            String actorId,
            String type,
            String targetType,
            String targetId,
            String title,
            String content,
            String dedupeKey
    ) {
        if (recipientId == null || recipientId.equals(actorId)) return;
        // 已拉黑的双方不再产生通知，避免通过通知侧信道继续骚扰对方。
        if (actorId != null && governanceMapper.existsBlockEitherDirection(actorId, recipientId)) return;
        if (!NOTIFICATION_TYPES.contains(type)) throw new IllegalArgumentException("通知类型无效");
        NotificationEntity entity = new NotificationEntity(
                UUID.randomUUID().toString(), recipientId, actorId, type, targetType, targetId,
                title, content, dedupeKey, null, Instant.now()
        );
        try {
            mapper.insertNotification(entity);
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        afterCommit(() -> {
            evictUnread(recipientId);
            realtimeEventService.publish(recipientId, "NOTIFICATION_CREATED", Map.of(
                    "notificationId", entity.id(), "notificationType", type
            ));
        });
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse notifications(String userId, String type, int page, int size) {
        String normalizedType = normalizeType(type);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new NotificationPageResponse(
                mapper.findNotificationPage(userId, normalizedType, safePage * safeSize, safeSize)
                        .stream().map(MessageService::toNotification).toList(),
                safePage, safeSize, mapper.countNotifications(userId, normalizedType)
        );
    }

    @Transactional
    public NotificationResponse markNotificationRead(String userId, String notificationId) {
        NotificationView current = mapper.findNotification(notificationId, userId);
        if (current == null) throw new IllegalArgumentException("通知不存在或不属于当前用户");
        mapper.markNotificationRead(notificationId, userId, Instant.now());
        afterCommit(() -> evictUnread(userId));
        return toNotification(mapper.findNotification(notificationId, userId));
    }

    @Transactional
    public void markAllNotificationsRead(String userId, String type) {
        mapper.markAllNotificationsRead(userId, normalizeType(type), Instant.now());
        afterCommit(() -> evictUnread(userId));
    }

    /** Redis Hash 缺失或不可用时按 MySQL 重建，刷新页面不会丢未读数。 */
    @Transactional(readOnly = true)
    public MessageUnreadResponse unread(String userId) {
        String key = unreadKey(userId);
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);
            if (!cached.isEmpty()) return unreadFromMap(cached);
        } catch (DataAccessException exception) {
            log.debug("Unread cache read failed: {}", exception.toString());
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        NOTIFICATION_TYPES.forEach(type -> counts.put(type, 0L));
        mapper.countUnreadNotifications(userId).forEach(row -> counts.put(row.type(), row.count()));
        long direct = mapper.countUnreadDirectMessages(userId);
        long total = direct + counts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, String> cache = new LinkedHashMap<>();
        counts.forEach((type, count) -> cache.put(type, Long.toString(count)));
        cache.put("DIRECT_MESSAGE", Long.toString(direct));
        cache.put("TOTAL", Long.toString(total));
        try {
            redisTemplate.opsForHash().putAll(key, cache);
        } catch (DataAccessException exception) {
            log.debug("Unread cache rebuild skipped: {}", exception.toString());
        }
        return new MessageUnreadResponse(total, direct, Map.copyOf(counts));
    }

    @Transactional(readOnly = true)
    public DirectConversationPageResponse conversations(String userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<DirectConversationResponse> items = mapper.findConversationPage(
                userId, safePage * safeSize, safeSize
        ).stream().map(view -> toConversation(view, userId)).toList();
        return new DirectConversationPageResponse(items, safePage, safeSize, mapper.countConversations(userId));
    }

    /** 读取消息前验证会话成员身份，随后把当前用户收到的消息标记为已读。 */
    @Transactional
    public DirectMessagePageResponse directMessages(String userId, String conversationId, int page, int size) {
        requireOwnedConversation(userId, conversationId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<DirectMessageResponse> items = mapper.findDirectMessagePage(
                conversationId, safePage * safeSize, safeSize
        ).stream().map(MessageService::toDirectMessage).toList();
        mapper.markConversationRead(conversationId, userId, Instant.now());
        afterCommit(() -> evictUnread(userId));
        return new DirectMessagePageResponse(
                items, safePage, safeSize, mapper.countDirectMessages(conversationId)
        );
    }

    /** clientMessageId 的唯一索引使网络重试返回原消息而不是插入重复内容。 */
    @Transactional
    public DirectMessageResponse send(String userId, SendDirectMessageRequest request) {
        //获取接收者ID
        String recipientId = request.recipientId().trim();
        //不可以自己发送给自己。
        if (userId.equals(recipientId)) throw new IllegalArgumentException("不能给自己发送私信");
        if (governanceMapper.existsBlockEitherDirection(userId, recipientId)) {
            throw new IllegalArgumentException("拉黑关系生效期间不能发送私信");
        }
        //验证接收者是否存在且可用
        UserEntity recipient = userMapper.findById(recipientId);
        if (recipient == null || !"ACTIVE".equals(recipient.status())) {
            throw new IllegalArgumentException("接收者不存在或当前不可用");
        }
        //查找是否已存在相同消息,clientMessageId	客户端生成的消息唯一标识符（类似 UUID）
        //同一个 clientMessageId 多次发送，只会创建一条消息.
        //实际场景 用户点击"发送"，网络慢，又点了一次。如果没有这个检查，会发送两条相同的消息！
        DirectMessageView existing = mapper.findDirectMessageByClientId(userId, request.clientMessageId());
        if (existing != null) return toDirectMessage(existing);
        //确定两个用户的顺序,保证 A 和 B 的对话只有一个会话，无论谁先发起！
        String low = userId.compareTo(recipientId) < 0 ? userId : recipientId;
        String high = userId.compareTo(recipientId) < 0 ? recipientId : userId;
        //查找现有会话
        DirectConversationEntity conversation = mapper.findConversationByPair(low, high);
        Instant now = Instant.now();
        //如果会话不存在，创建一个新的会话
        if (conversation == null) {
            DirectConversationEntity candidate = new DirectConversationEntity(
                    UUID.randomUUID().toString(), low, high, null, now, now
            );
            try {
                mapper.insertConversation(candidate);
                conversation = candidate;
                //处理并发冲突，
            } catch (DuplicateKeyException race) {
                conversation = mapper.findConversationByPair(low, high);
            }
        }
        //构建一个消息实体
        DirectMessageEntity entity = new DirectMessageEntity(
                UUID.randomUUID().toString(), // 新消息 ID
                conversation.id(), // 所属会话 ID
                userId, // 发送者 ID
                recipientId,// 接收者 ID
                request.clientMessageId(), // 客户端消息 ID（用于幂等）
                request.content().trim(),  // 消息内容（去除空格）
                null, // 已读时间（未读）
                now// 发送时间
        );
        try {
            //插入消息队列
            mapper.insertDirectMessage(entity);
        } catch (DuplicateKeyException duplicate) {
            return toDirectMessage(mapper.findDirectMessageByClientId(userId, request.clientMessageId()));
        }
        //更新会话的最后消息时间
        mapper.touchConversation(conversation.id(), now);
        //异步通知
        String messageId = entity.id();//消息 ID
        String conversationId = conversation.id();// 会话 ID
        //afterCommit用于事务同步。
        //事务同步 只有当数据库事务成功提交后才执行这些操作！ 如果事务回滚了（比如数据库错误），这些代码不会执行！
        afterCommit(() -> {
            //清除Redis缓存，删除接收者的未读数缓存。
            evictUnread(recipientId);
            //发布实时事件，通知接收者有新消息。
            realtimeEventService.publish(
                recipientId,
                "DIRECT_MESSAGE_CREATED",
                Map.of(
                    "messageId", messageId,
                    "conversationId", conversationId,
                    "senderId", userId
            ));
        });
        //查询并返回完整的消息信息
        return toDirectMessage(mapper.findDirectMessage(entity.id()));
    }

    private DirectConversationEntity requireOwnedConversation(String userId, String conversationId) {
        DirectConversationEntity conversation = mapper.findOwnedConversation(conversationId, userId);
        if (conversation == null) throw new IllegalArgumentException("私信会话不存在或不属于当前用户");
        return conversation;
    }

    private static NotificationResponse toNotification(NotificationView view) {
        return new NotificationResponse(
                view.id(), view.actorId(), view.actorUsername(), view.actorDisplayName(), view.actorAvatarUrl(),
                view.notificationType(), view.targetType(), view.targetId(), view.title(), view.content(),
                view.readAt() != null, view.createdAt()
        );
    }

    private static DirectConversationResponse toConversation(DirectConversationView view, String userId) {
        return new DirectConversationResponse(
                view.id(), view.otherUserId(), view.otherUsername(), view.otherDisplayName(), view.otherAvatarUrl(),
                view.lastMessageContent(), userId.equals(view.lastMessageSenderId()), view.lastMessageAt(),
                view.unreadCount(), view.createdAt(), view.updatedAt()
        );
    }

    private static DirectMessageResponse toDirectMessage(DirectMessageView view) {
        return new DirectMessageResponse(
                view.id(), view.conversationId(), view.senderId(), view.senderUsername(),
                view.senderDisplayName(), view.senderAvatarUrl(), view.recipientId(), view.clientMessageId(),
                view.content(), view.readAt() != null, view.createdAt()
        );
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase();
        if (!NOTIFICATION_TYPES.contains(normalized)) throw new IllegalArgumentException("通知类型筛选无效");
        return normalized;
    }

    private static MessageUnreadResponse unreadFromMap(Map<Object, Object> values) {
        Map<String, Long> notifications = new LinkedHashMap<>();
        NOTIFICATION_TYPES.forEach(type -> notifications.put(type, parse(values.get(type))));
        return new MessageUnreadResponse(
                parse(values.get("TOTAL")), parse(values.get("DIRECT_MESSAGE")), Map.copyOf(notifications)
        );
    }

    private void evictUnread(String userId) {
        try {
            redisTemplate.delete(unreadKey(userId));
        } catch (DataAccessException exception) {
            log.debug("Unread cache eviction skipped: {}", exception.toString());
        }
    }

    private static String unreadKey(String userId) {
        return "message:unread:" + userId;
    }

    private static long parse(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** 只有事务成功提交后才更新缓存和推送，防止客户端看见随后回滚的通知。 */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
