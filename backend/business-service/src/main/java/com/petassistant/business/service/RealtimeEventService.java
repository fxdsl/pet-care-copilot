package com.petassistant.business.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.RealtimeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 实时事件出口：Redis Stream 保留有界重放记录，Pub/Sub 负责跨实例在线广播。
 * MySQL 通知和私信表才是离线事实，Redis 故障不会让业务写入失败。
 */
@Service
public class RealtimeEventService {

    public static final String CHANNEL = "message:realtime:channel";
    private static final String STREAM = "message:event:stream";
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeConnectionRegistry registry;

    public RealtimeEventService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RealtimeConnectionRegistry registry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    /** 发布的 payload 只带业务 ID 和状态，不把私信正文或敏感档案写入 Redis Stream。 */
    //实时消息推送。
    public void publish(String recipientId, String type, Map<String, String> payload) {
        //创建实时事件封包，包含事件 ID、接收者 ID、事件类型、事件负载和创建时间。
        RealtimeEnvelope envelope = new RealtimeEnvelope(
                UUID.randomUUID().toString(), // 生成唯一事件 ID
                recipientId,// 接收者
                type,// 事件类型
                Map.copyOf(payload),// 复制 payload（防止外部修改）
                Instant.now()// 事件创建时间
        );

        String json;
        try {
            //序列化实时事件封包为 JSON 字符串。
            //objectMapper	Jackson 库的 ObjectMapper 实例
            //.writeValueAsString()	将 Java 对象序列化为 JSON 字符串
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实时事件序列化失败", exception);
        }
        //Redis核心操作
        try {
            //Stream Key: message:event:stream
            //
            //┌─────────────────────────────────────────────────────────────┐
            //│ Field           │ Value                                      │
            //├─────────────────┼────────────────────────────────────────────┤
            //│ eventId         │ 550e8400-e29b-41d4-a716-446655440000      │
            //│ recipientId     │ user_123                                   │
            //│ type            │ DIRECT_MESSAGE_CREATED                     │
            //│ payload         │ {"messageId":"msg_abc","conversationId":"conv_xyz"} │
            //│ createdAt       │ 2026-08-25T02:30:00.123Z                  │
            //└─────────────────┴────────────────────────────────────────────┘
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", envelope.eventId());
            fields.put("recipientId", recipientId);
            fields.put("type", type);
            fields.put("payload", objectMapper.writeValueAsString(payload));
            fields.put("createdAt", envelope.createdAt().toString());
            //将实时事件封包添加到 Redis Stream 中。
            //StreamRecords.mapBacked(fields)将 Map 字段转换为 Stream 记录格式
            // .withStreamKey(STREAM)指定 Stream 的 key 名称
            redisTemplate.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(STREAM));
            //保留最近 1000 条记录，超过 10000 条的记录会被删除。
            redisTemplate.opsForStream().trim(STREAM, 10_000);
            //将实时事件封包发送到 Redis Pub/Sub 通道。
            redisTemplate.convertAndSend(CHANNEL, json);
        } catch (DataAccessException | JsonProcessingException exception) {
            // 单机开发时 Redis 暂停仍可向本实例在线连接推送；离线数据可从 MySQL 恢复。
            log.warn("Realtime Redis publish failed, using local fallback: {}", exception.toString());
            //将实时事件封包发送到对应用户实例的所有在线连接。
            registry.sendRaw(recipientId, json);
        }
    }

    /** 每个服务器实例收到 Redis 推送的 JSON 消息后：
     反序列化 JSON 为 RealtimeEnvelope 对象
     提取 recipientId（目标接收用户的 ID）
     调用 registry.sendRaw() 将消息推送给当前实例上属于该用户的所有 WebSocket 连接 */
    public void receive(String json) {
        try {
            // 步骤 1: 解析 JSON，提取 recipientId
            RealtimeEnvelope envelope = objectMapper.readValue(json, RealtimeEnvelope.class);
            // 步骤 2: 只向当前实例上属于该用户的连接推送
            registry.sendRaw(envelope.recipientId(), json);
        } catch (JsonProcessingException exception) {
            log.warn("Ignored malformed realtime event: {}", exception.toString());
        }
    }
}
