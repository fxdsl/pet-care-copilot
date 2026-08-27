package com.petassistant.business.service;

import java.util.List;
import java.util.Map;

import com.petassistant.business.data.dto.internal.UnreadCountRow;
import com.petassistant.business.data.entity.NotificationEntity;
import com.petassistant.business.data.mapper.MessageMapper;
import com.petassistant.business.data.mapper.CommunityGovernanceMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.data.dto.request.SendDirectMessageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 第十周消息幂等和 Redis 未读回源测试。 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageMapper mapper;
    @Mock CommunityGovernanceMapper governanceMapper;
    @Mock UserMapper userMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock HashOperations<String, Object, Object> hashOperations;
    @Mock RealtimeEventService realtimeEventService;

    @Test
    void duplicateNotificationDoesNotPushOrIncreaseUnreadAgain() {
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertNotification(any(NotificationEntity.class));

        service().createNotification(
                "recipient", "actor", "LIKE", "POST", "post-1",
                "有人赞了你的动态", "测试", "LIKE:post-1:actor"
        );

        verify(realtimeEventService, never()).publish(any(), any(), any());
    }

    @Test
    void unreadCacheMissRebuildsEveryCategoryFromMysql() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("message:unread:user-1")).thenReturn(Map.of());
        when(mapper.countUnreadNotifications("user-1")).thenReturn(List.of(
                new UnreadCountRow("COMMENT", 2), new UnreadCountRow("FOLLOW", 1)
        ));
        when(mapper.countUnreadDirectMessages("user-1")).thenReturn(3L);

        var response = service().unread("user-1");

        assertThat(response.total()).isEqualTo(6);
        assertThat(response.directMessages()).isEqualTo(3);
        assertThat(response.notifications()).containsEntry("COMMENT", 2L).containsEntry("FOLLOW", 1L);
        verify(hashOperations).putAll(any(), any());
    }

    @Test
    void blockRelationRejectsDirectMessageBeforeCreatingConversation() {
        when(governanceMapper.existsBlockEitherDirection("user-1", "user-2")).thenReturn(true);

        assertThatThrownBy(() -> service().send("user-1", new SendDirectMessageRequest(
                "user-2", "client-message-1", "你好"
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能发送私信");

        verify(mapper, never()).insertDirectMessage(any());
    }

    private MessageService service() {
        return new MessageService(mapper, governanceMapper, userMapper, redisTemplate, realtimeEventService);
    }
}
