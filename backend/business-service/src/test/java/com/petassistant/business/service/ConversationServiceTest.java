package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import com.petassistant.business.data.dto.request.CreateMessageRequest;
import com.petassistant.business.data.entity.ConversationEntity;
import com.petassistant.business.data.entity.MessageEntity;
import com.petassistant.business.data.mapper.ConversationMapper;
import com.petassistant.business.exception.ConversationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ConversationService 最近上下文 Cache-Aside 测试。 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationMapper mapper;

    @Mock
    private ConversationContextCacheService contextCacheService;

    @InjectMocks
    private ConversationService service;

    /** Redis 命中时仍先用 MySQL 校验会话所有权，但不读取消息表。 */
    @Test
    void shouldReturnRedisContextWithoutMysqlQuery() {
        List<ConversationContextMessage> cached = List.of(
                new ConversationContextMessage("USER", "第一问"),
                new ConversationContextMessage("ASSISTANT", "第一答")
        );
        when(contextCacheService.get("conversation-1")).thenReturn(Optional.of(cached));
        when(mapper.findByIdAndUser("conversation-1", "user-1")).thenReturn(conversation(Instant.now()));

        List<ConversationContextMessage> result =
                service.getRecentMessagesForContext("user-1", "conversation-1", 12);

        assertThat(result).containsExactlyElementsOf(cached);
        verify(mapper, never()).findRecentMessages(any(), anyInt());
    }

    /** Redis 未命中时从 MySQL 读取并回填缓存。 */
    @Test
    void shouldLoadMysqlAndPopulateContextCache() {
        Instant now = Instant.now();
        when(contextCacheService.get("conversation-1")).thenReturn(Optional.empty());
        when(mapper.findByIdAndUser("conversation-1", "user-1")).thenReturn(conversation(now));
        when(mapper.findRecentMessages("conversation-1", 12)).thenReturn(List.of(
                new MessageEntity("m1", "conversation-1", "USER", "第一问", null, null, now)
        ));

        List<ConversationContextMessage> result =
                service.getRecentMessagesForContext("user-1", "conversation-1", 12);

        assertThat(result).containsExactly(new ConversationContextMessage("USER", "第一问"));
        verify(contextCacheService).put("conversation-1", result);
    }

    /** 新消息写入后必须注册提交后失效，而不是在事务提交前直接改缓存。 */
    @Test
    void shouldInvalidateContextAfterMessageCommit() {
        when(mapper.findByIdAndUser("conversation-1", "user-1")).thenReturn(conversation(Instant.now()));

        service.addMessage(
                "user-1",
                "conversation-1",
                new CreateMessageRequest("USER", "幼猫一天喂几次？", null, null)
        );

        verify(mapper).insertMessage(any());
        verify(mapper).touchConversation(any(), any());
        verify(contextCacheService).evictAfterCommit("conversation-1");
    }

    /** 即使攻击者知道会话 UUID，也不能命中 Redis 读取他人上下文。 */
    @Test
    void shouldRejectConversationBeforeReadingCacheWhenOwnerDiffers() {
        when(mapper.findByIdAndUser("conversation-1", "attacker")).thenReturn(null);

        assertThatThrownBy(() -> service.getRecentMessagesForContext("attacker", "conversation-1", 12))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(contextCacheService, never()).get("conversation-1");
    }

    /** 构造存在的会话实体。 */
    private static ConversationEntity conversation(Instant now) {
        return new ConversationEntity("conversation-1", "user-1", "幼猫喂养", "ACTIVE", now, now);
    }
}
