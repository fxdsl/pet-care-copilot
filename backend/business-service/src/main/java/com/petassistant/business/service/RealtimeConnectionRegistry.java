package com.petassistant.business.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 保存当前 Java 实例上的已认证 WebSocket 连接，并维护带 TTL 的在线状态。 */
@Component
public class RealtimeConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeConnectionRegistry.class);
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(75);
    private static final int MAX_CONNECTIONS_PER_USER = 3;

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userBySession = new ConcurrentHashMap<>();

    public RealtimeConnectionRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 注册认证后的连接；限制同一账号连接数，避免单账号耗尽容器资源。 */
    public boolean register(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        if (sessions.size() >= MAX_CONNECTIONS_PER_USER) return false;
        sessions.add(session);
        userBySession.put(session.getId(), userId);
        touch(userId);
        return true;
    }

    /** 心跳续期只写非敏感在线标记。 */
    public void touch(String userId) {
        try {
            redisTemplate.opsForValue().set("message:presence:" + userId, "ONLINE", PRESENCE_TTL);
        } catch (DataAccessException exception) {
            log.debug("Presence refresh skipped because Redis is unavailable: {}", exception.toString());
        }
    }

    public String userId(WebSocketSession session) {
        return userBySession.get(session.getId());
    }

    /** 向当前实例上属于目标用户的全部标签页推送同一事件。 */
    //userId目标接收者的用户 ID	"user_123"
    //json	要发送的 JSON 消息 	{"eventId":"xxx",...}
    public void sendRaw(String userId, String json) {
        //查找该用户的所有 WebSocket 会话
        Set<WebSocketSession> sessions =
            sessionsByUser.getOrDefault(userId, Set.of());
        //遍历所有会话，发送 JSON 消息
        //Set.copyOf(sessions)确保在遍历过程中不会修改集合大小
        //场景：用户有 3 个连接 [A, B, C]
        //
        //遍历过程中：
        //┌───┬───┬───┐
        //│ A │ B │ C │  ← 正在遍历
        //└───┴───┴───┘
        //  │
        //  ▼
        //发现 A 已关闭 → 删除 A
        //┌───┬───┐
        //│ B │ C │  ← 集合结构改变！
        //└───┴───┘
        //  │
        //  ▼
        //继续遍历 → ❌ 抛出异常！
        //（迭代器检测到集合被修改）
        for (WebSocketSession session : Set.copyOf(sessions)) {
            //检查连接是否仍然打开，避免发送到已关闭的连接
            if (!session.isOpen()) {
                remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    //发送 JSON 消息到该会话
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException exception) {
                log.debug("Realtime session send failed: {}", exception.toString());
                remove(session);
            }
        }
    }

    /** 连接断开后只在用户没有其他标签页时删除在线状态。 */
    public void remove(WebSocketSession session) {
        String userId = userBySession.remove(session.getId());
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
                try {
                    redisTemplate.delete("message:presence:" + userId);
                } catch (DataAccessException exception) {
                    log.debug("Presence delete skipped: {}", exception.toString());
                }
            }
        }
    }

    public void rejectTooMany(WebSocketSession session) throws IOException {
        session.close(CloseStatus.POLICY_VIOLATION.withReason("同一账号实时连接数量已达上限"));
    }
}
