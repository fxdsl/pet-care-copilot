package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.dto.internal.DirectConversationView;
import com.petassistant.business.data.dto.internal.DirectMessageView;
import com.petassistant.business.data.dto.internal.NotificationView;
import com.petassistant.business.data.dto.internal.UnreadCountRow;
import com.petassistant.business.data.entity.DirectConversationEntity;
import com.petassistant.business.data.entity.DirectMessageEntity;
import com.petassistant.business.data.entity.NotificationEntity;
import org.apache.ibatis.annotations.Param;

/** 第十周消息 MyBatis Mapper；通知和私信的最终状态全部保存在 MySQL。 */
public interface MessageMapper {

    int insertNotification(NotificationEntity notification);

    NotificationView findNotification(@Param("id") String id, @Param("recipientId") String recipientId);

    List<NotificationView> findNotificationPage(
            @Param("recipientId") String recipientId,
            @Param("type") String type,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countNotifications(@Param("recipientId") String recipientId, @Param("type") String type);

    List<UnreadCountRow> countUnreadNotifications(@Param("recipientId") String recipientId);

    int markNotificationRead(
            @Param("id") String id,
            @Param("recipientId") String recipientId,
            @Param("readAt") Instant readAt
    );

    int markAllNotificationsRead(
            @Param("recipientId") String recipientId,
            @Param("type") String type,
            @Param("readAt") Instant readAt
    );

    DirectConversationEntity findConversationByPair(
            @Param("participantLowId") String participantLowId,
            @Param("participantHighId") String participantHighId
    );

    DirectConversationEntity findOwnedConversation(@Param("id") String id, @Param("userId") String userId);

    int insertConversation(DirectConversationEntity conversation);

    List<DirectConversationView> findConversationPage(
            @Param("userId") String userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countConversations(@Param("userId") String userId);

    int touchConversation(@Param("id") String id, @Param("lastMessageAt") Instant lastMessageAt);

    int insertDirectMessage(DirectMessageEntity message);

    DirectMessageView findDirectMessage(@Param("id") String id);

    DirectMessageView findDirectMessageByClientId(
            @Param("senderId") String senderId,
            @Param("clientMessageId") String clientMessageId
    );

    List<DirectMessageView> findDirectMessagePage(
            @Param("conversationId") String conversationId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countDirectMessages(@Param("conversationId") String conversationId);

    long countUnreadDirectMessages(@Param("recipientId") String recipientId);

    int markConversationRead(
            @Param("conversationId") String conversationId,
            @Param("recipientId") String recipientId,
            @Param("readAt") Instant readAt
    );
}
