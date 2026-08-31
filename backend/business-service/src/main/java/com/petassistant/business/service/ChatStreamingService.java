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
    //每个流的处理时间（如模型调用）可能不同，线程池确保并发处理
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, StreamSession> sessions = new ConcurrentHashMap<>();

    public ChatStreamingService(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /** 创建或重连一条流；同 requestId 但不同用户/问题会被拒绝。 */
    //客户端请求 →[1]容量检查 → [2]获取/创建会话 →
    // [3]身份验证 → [4]创建Emitter → [5]附加+回放 → [6]启动任务 → 返回Emitter
    public SseEmitter open(String userId, ChatStreamRequest request, long lastEventId) {
        //从内存中查找是否已有该请求的会话记录。快速判断是"新请求"还是"重连"
        StreamSession existing = sessions.get(request.requestId());
        //如果是新请求 且 当前活跃连接数 ≥ 50 (MAX_ACTIVE_STREAMS)
        //抛出异常拒绝服务，防止服务器过载
        if (existing == null && activeCount() >= MAX_ACTIVE_STREAMS) {
            throw new IllegalStateException("当前流式问答连接较多，请稍后重试");
        }
        //如果为新请求，创建新会话记录，否则直接返回已存在的会话记录
        //两个线程同时到达 → 只有一个执行lambda表达式
        StreamSession session = sessions.computeIfAbsent(request.requestId(), ignored ->
                new StreamSession(request.requestId(), userId, request.question().trim())
        );
        //如果是重连，检查用户ID和问题是否匹配,
        // session.userId.equals("attacker-id")防止出现会话劫持问题
        //session.question.equals("篡改后的问题内容"),如果没有校验： 可能导致AI回答与问题不一致！
        if (!session.userId.equals(userId) || !session.question.equals(request.question().trim())) {
            throw new IllegalArgumentException("requestId 已用于其他问答请求");
        }
        //创建 SSE 连接通道
        // 什么是 SseEmitter？
        //Spring提供的 Server-Sent Events 发射器
        //基于 HTTP 长连接，服务端可主动推送数据给客户端
        //EMITTER_TIMEOUT_MS = 135_000L  // 135秒 = 2分15秒
        //超时时间：如果135秒内没有数据传输，连接自动关闭
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        //附加 Emitter 并回放历史
        session.attach(emitter, Math.max(0, lastEventId));
        //启动任务
        //为什么需要 synchronized？
        //防止并发启动：两个请求同时到达（虽然罕见），可能导致任务重复执行
        synchronized (session) {
            //if (!session.started) 幂等检查
            // 场景1: 首次调用
            //session.started = false → 进入if块 → 设置为true → 启动任务 ✅
            //
            // 场景2: 重连调用
            //session.started = true → 跳过if块 → 不重复启动 ✅
            //
            // 场景3: 并发竞争（两个线程同时进入synchronized）
            //Thread A: 检查 started=false → 进入if → 设置started=true → 提交任务
            //Thread B: 检查 started=true → 跳过if → 结束 ✅
            if (!session.started) {
                session.started = true;
                //线程池提交任务，最多8个线程并发处理
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
            //发送准备阶段事件
            session.emit("stage", json(Map.of(
                    "stage", "PREPARING", "message", "正在准备会话、宠物档案和候选知识"
            )));
            //调用AI服务，获取流式回答
            ChatResponse response = chatService.answerStreaming(
                    session.userId,
                    request.toChatRequest(),
                    event -> forwardAgentEvent(session, event)
            );
            //如果用户已取消，直接返回
            if (session.cancelled) return;
            //发送最终结果
            session.emit("result", objectMapper.writeValueAsString(response));
            //发送完成事件，包含会话ID和终止原因
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
                //将事件设置为已完成状态
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

    /** 获取当前活跃流数量。 */
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

        /**承担着事件分发、历史记录、连接管理三大职责*/
        //private	仅限内部使用（外部通过 open() 和 cancel() 间接调用）
        //synchronized	线程安全锁 - 保证同一时刻只有一个线程能执行此方法
        //void	无返回值（fire-and-forget 模式）
        //可能的并发场景：
        //├── 线程A: run() 方法中调用 session.emit("token", "根据...")
        //├── 线程B: cancel() 方法中调用 session.emit("cancelled", "...")
        //└── 线程C: attach() 中正在遍历 events 列表
        //
        //如果没有锁：
        //→ events.add() 和 events 遍历同时发生 → ConcurrentModificationException 💥
        //→ sequence.incrementAndGet() 产生重复ID → 数据错乱 💥
        private synchronized void emit(String eventName, String data) {
            //如果事件已完成，直接返回
            if (finished) return;
            //创建事件对象 + 分配ID
            StoredEvent event = new StoredEvent(sequence.incrementAndGet(), eventName, data);
            //存入历史缓冲区,方便断点续传
            events.add(event);
            if (events.size() > MAX_REPLAY_EVENTS) events.remove(0);
            //广播事件到所有活跃连接
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
                //SSE用于向客户端发送事件，每个事件包含ID、名称和数据部分
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.id))
                        .name(event.name)
                        .data(event.data, MediaType.APPLICATION_JSON));
                return true;//发送成功
            } catch (IOException | IllegalStateException exception) {
                return false;//发送失败
            }
        }
    }

    private record StoredEvent(long id, String name, String data) {
    }
}
