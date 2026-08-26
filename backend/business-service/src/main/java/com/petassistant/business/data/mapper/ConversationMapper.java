package com.petassistant.business.data.mapper;

import java.time.Instant;
import java.util.List;

import com.petassistant.business.data.entity.ConversationEntity;
import com.petassistant.business.data.entity.MessageEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 会话与消息 MyBatis Mapper。
 * SQL 统一放在 resources/mapper/ConversationMapper.xml，接口只声明数据操作语义。
 */
public interface ConversationMapper {

    /** 写入一个新会话。 */
    int insertConversation(ConversationEntity conversation);

    /** 按主键查询会话，不存在时返回 null。 */
    ConversationEntity findByIdAndUser(@Param("id") String id, @Param("userId") String userId);

    /** 按更新时间倒序查询最近会话。 */
    List<ConversationEntity> findRecentByUser(@Param("userId") String userId, @Param("limit") int limit);

    /** 写入一条会话消息。 */
    int insertMessage(MessageEntity message);

    /** 新消息写入后同步更新会话最后活动时间。 */
    int touchConversation(@Param("conversationId") String conversationId, @Param("updatedAt") Instant updatedAt);

    /** 按时间顺序查询一个会话的全部消息。 */
    List<MessageEntity> findMessages(@Param("conversationId") String conversationId);

    /** 查询最近若干条消息并恢复为正序，供多轮模型上下文使用。 */
    List<MessageEntity> findRecentMessages(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit
    );

}
