package com.petassistant.business.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.petassistant.business.data.dto.internal.AiAgentRequest;
import com.petassistant.business.data.dto.internal.AiAgentResponse;
import com.petassistant.business.data.dto.internal.AiStreamEvent;
import com.petassistant.business.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * FastAPI LangGraph Agent 与健康检查客户端。
 * 该类只处理远程协议，不读取 MySQL，也不承载业务筛选规则。
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    /** 注入统一配置的 HTTP/1.1 RestClient。 */
    public AiServiceClient(@Qualifier("aiRestClient") RestClient aiRestClient, ObjectMapper objectMapper) {
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 FastAPI SSE，并返回 result 事件中的最终响应。
     * 网络层逐行处理，避免把整段回答缓冲完后才交给浏览器。
     * 流已经向浏览器发送片段后不能自动重试，否则会重复片段和模型计费。
     */
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiAgentResponse answerStreaming(AiAgentRequest request, Consumer<AiStreamEvent> listener) {
        AtomicReference<AiAgentResponse> result = new AtomicReference<>();
        try {
            aiRestClient.post()
                    .uri("/api/v1/agent/answer/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(request)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new IllegalStateException("AI 流服务返回 HTTP " + response.getStatusCode().value());
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                response.getBody(), StandardCharsets.UTF_8
                        ))) {
                            String id = null;
                            String event = "message";
                            StringBuilder data = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isEmpty()) {
                                    if (!data.isEmpty()) {
                                        AiStreamEvent streamEvent = new AiStreamEvent(id, event, data.toString());
                                        listener.accept(streamEvent);
                                        if ("result".equals(event)) {
                                            result.set(objectMapper.readValue(data.toString(), AiAgentResponse.class));
                                        }
                                    }
                                    id = null;
                                    event = "message";
                                    data.setLength(0);
                                } else if (line.startsWith("id:")) {
                                    id = line.substring(3).trim();
                                } else if (line.startsWith("event:")) {
                                    event = line.substring(6).trim();
                                } else if (line.startsWith("data:")) {
                                    if (!data.isEmpty()) data.append('\n');
                                    data.append(line.substring(5).trim());
                                }
                            }
                        }
                        return null;
                    });
            if (result.get() == null) throw new IllegalStateException("AI 流缺少最终 result 事件");
            return result.get();
        } catch (Exception error) {
            log.warn("Agent streaming call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }

    /**
     * 将问题和候选知识分块发送给 FastAPI，获得排序后的来源和答案。
     * 通用模型调用不自动重试，避免一次问题消耗两次百炼额度。
     */
    @CircuitBreaker(name = "aiService")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE)
    public AiAgentResponse answer(AiAgentRequest request) {
        try {
            AiAgentResponse response = aiRestClient.post()
                .uri("/api/v1/agent/answer")
                .body(request)
                .retrieve()
                .body(AiAgentResponse.class);
            if (response == null) {
                throw new IllegalStateException("AI 服务返回空响应");
            }
            return response;
        } catch (Exception error) {
            log.warn("Agent answer call failed: {}", error.toString());
            throw new AiServiceUnavailableException(error);
        }
    }

    /**
     * 检查 FastAPI 是否可访问；健康检查失败只返回 false，不影响当前 Java 进程。
     */
    public boolean isHealthy() {
        try {
            AiHealthResponse response = aiRestClient.get()
                    .uri("/api/v1/ai/health")
                    .retrieve()
                    .body(AiHealthResponse.class);
            return response != null && "UP".equalsIgnoreCase(response.status());
        } catch (Exception error) {
            log.debug("FastAPI health check failed", error);
            return false;
        }
    }

    /** FastAPI 健康响应的最小内部投影。 */
    private record AiHealthResponse(String status) {
    }
}
