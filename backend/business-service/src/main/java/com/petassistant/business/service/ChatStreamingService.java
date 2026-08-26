package com.petassistant.business.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.AiStreamEvent;
import com.petassistant.business.data.dto.request.ChatStreamRequest;
import com.petassistant.business.data.dto.response.ChatResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 浏览器 SSE 会话管理器：固定线程池、连接上限、事件回放、停止生成和完成后短期保留。
 * Last-Event-ID 只重放本次 requestId 已产生的事件，不会再次保存 USER/ASSISTANT 消息。
 */
@Service
public class ChatStreamingService {

    private static final int MAX_ACTIVE_STREAMS = 50;
    private static final int MAX_REPLAY_EVENTS = 2_000;
    private static final long EMITTER_TIMEOUT_MS = 135_000L;

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, StreamSession> sessions = new ConcurrentHashMap<>();

    public ChatStreamingService(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /** 创建或重连一条流；同 requestId 但不同用户/问题会被拒绝。 */
    public SseEmitter open(String userId, ChatStreamRequest request, long lastEventId) {
        StreamSession existing = sessions.get(request.requestId());
        if (existing == null && activeCount() >= MAX_ACTIVE_STREAMS) {
            throw new IllegalStateException("当前流式问答连接较多，请稍后重试");
        }
        StreamSession session = sessions.computeIfAbsent(request.requestId(), ignored ->
                new StreamSession(request.requestId(), userId, request.question().trim())
        );
        if (!session.userId.equals(userId) || !session.question.equals(request.question().trim())) {
            throw new IllegalArgumentException("requestId 已用于其他问答请求");
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        session.attach(emitter, Math.max(0, lastEventId));
        synchronized (session) {
            if (!session.started) {
                session.started = true;
                session.future = workers.submit(() -> run(session, request));
            }
        }
        return emitter;
    }

    /** 停止后保留已产生事件，客户端可以看到 CANCELLED，而不会重新开始相同 requestId。 */
    public void cancel(String userId, String requestId) {
        StreamSession session = sessions.get(requestId);
        if (session == null || !session.userId.equals(userId)) throw new IllegalArgumentException("流请求不存在");
        synchronized (session) {
            if (session.finished) return;
            session.cancelled = true;
            if (session.future != null) session.future.cancel(true);
            session.emit("cancelled", json(Map.of("message", "已停止生成")));
            session.finish();
            scheduleRemoval(session.requestId);
        }
    }

    private void run(StreamSession session, ChatStreamRequest request) {
        try {
            session.emit("stage", json(Map.of(
                    "stage", "PREPARING", "message", "正在准备会话、宠物档案和候选知识"
            )));
            ChatResponse response = chatService.answerStreaming(
                    session.userId,
                    request.toChatRequest(),
                    event -> forwardAgentEvent(session, event)
            );
            if (session.cancelled) return;
            session.emit("result", objectMapper.writeValueAsString(response));
            session.emit("done", json(Map.of(
                    "conversationId", response.conversationId(),
                    "terminationReason", response.terminationReason()
            )));
            session.finish();
        } catch (Exception exception) {
            if (!session.cancelled) {
                session.emit("error", json(Map.of(
                        "code", "STREAM_FAILED", "message", "流式回答失败，请重试"
                )));
                session.finish();
            }
        } finally {
            scheduleRemoval(session.requestId);
        }
    }

    /** FastAPI 的 result/done 要等 Java 成功保存消息后再发；其他脱敏事件可以即时转发。 */
    private void forwardAgentEvent(StreamSession session, AiStreamEvent event) {
        if (session.cancelled || "result".equals(event.event()) || "done".equals(event.event())) return;
        if (List.of("stage", "token", "heartbeat", "error").contains(event.event())) {
            session.emit(event.event(), event.data());
        }
    }

    private int activeCount() {
        return (int) sessions.values().stream().filter(session -> !session.finished).count();
    }

    private void scheduleRemoval(String requestId) {
        cleanup.schedule(() -> sessions.remove(requestId), 10, TimeUnit.MINUTES);
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SSE 事件序列化失败", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        cleanup.shutdownNow();
    }

    /** 单个 requestId 的有界事件缓冲；断线只移除 emitter，不停止后台任务。 */
    private static final class StreamSession {
        private final String requestId;
        private final String userId;
        private final String question;
        private final Instant createdAt = Instant.now();
        private final AtomicLong sequence = new AtomicLong();
        private final List<StoredEvent> events = new ArrayList<>();
        private final List<SseEmitter> emitters = new ArrayList<>();
        private boolean started;
        private boolean finished;
        private boolean cancelled;
        private Future<?> future;

        private StreamSession(String requestId, String userId, String question) {
            this.requestId = requestId;
            this.userId = userId;
            this.question = question;
        }

        private synchronized void attach(SseEmitter emitter, long lastEventId) {
            emitters.add(emitter);
            emitter.onCompletion(() -> detach(emitter));
            emitter.onTimeout(() -> detach(emitter));
            emitter.onError(error -> detach(emitter));
            for (StoredEvent event : events) {
                if (event.id > lastEventId && !send(emitter, event)) break;
            }
            if (finished) emitter.complete();
        }

        private synchronized void emit(String eventName, String data) {
            if (finished) return;
            StoredEvent event = new StoredEvent(sequence.incrementAndGet(), eventName, data);
            events.add(event);
            if (events.size() > MAX_REPLAY_EVENTS) events.remove(0);
            emitters.removeIf(emitter -> !send(emitter, event));
        }

        private synchronized void finish() {
            finished = true;
            emitters.forEach(SseEmitter::complete);
            emitters.clear();
        }

        private synchronized void detach(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        private static boolean send(SseEmitter emitter, StoredEvent event) {
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.id))
                        .name(event.name)
                        .data(event.data, MediaType.APPLICATION_JSON));
                return true;
            } catch (IOException | IllegalStateException exception) {
                return false;
            }
        }
    }

    private record StoredEvent(long id, String name, String data) {
    }
}
