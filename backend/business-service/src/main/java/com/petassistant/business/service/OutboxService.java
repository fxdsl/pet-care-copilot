package com.petassistant.business.service;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.entity.OutboxEventEntity;
import com.petassistant.business.data.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;

/** 在调用方业务事务中写入通用 Outbox，序列化失败会让业务一起回滚。 */
@Service
public class OutboxService {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(String aggregateType, String aggregateId, String eventType, String actorUserId) {
        //记录业务事件到 Outbox 表
        //public record OutboxEventEntity(
        //    String id,              // ① 事件ID

        //    String aggregateType,   // ② 聚合类型作用：标识属于哪个业务模块
        //可能的值：
        //"KNOWLEDGE_SUBMISSION" - 知识投稿
        //"COMMUNITY_POST" - 社区帖子
        //"USER_PROFILE" - 用户资料

        //    String aggregateId,     // ③ 业务实体ID,具体是哪个业务对象触发了这个事件
        //    String eventType,       // ④ 事件类型
        //作用：描述发生了什么动作
        //命名规范：{聚合}_{动作}_状态
        //示例值：
        //"KNOWLEDGE_PRECHECK_REQUESTED" - 请求预检
        //"KNOWLEDGE_APPROVED" - 审核通过
        //"KNOWLEDGE_REJECTED" - 审核驳回
        //"COMMUNITY_POST_PUBLISHED" - 帖子发布


        //    String payloadJson,     // ⑤ 事件载荷（JSON）
        //    String status,          // ⑥ 处理状态
        //    int attempts,           // ⑦ 重试次数
        //    Instant nextAttemptAt,  // ⑧ 下次重试时间
        //    Instant createdAt,      // ⑨ 创建时间
        //    Instant publishedAt     // ⑩ 发布时间
        //) { }
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        CommunityEventPayload payload = new CommunityEventPayload(
                eventId, eventType, aggregateId, actorUserId, now
        );
        try {
            mapper.insert(new OutboxEventEntity(
                    eventId, aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(payload), "PENDING", 0, now, now, null
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化业务事件", exception);
        }
    }
}
