package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.ChatRequest;
import com.petassistant.business.data.dto.request.ChatStreamRequest;
import com.petassistant.business.data.dto.response.ChatResponse;
import com.petassistant.business.service.ChatService;
import com.petassistant.business.service.ChatStreamingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 宠物问答 HTTP 入口，只处理参数校验和状态码，Agent 编排交给 ChatService。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatStreamingService streamingService;

    /** 使用构造器注入问答服务，便于单元测试替换。 */
    public ChatController(ChatService chatService, ChatStreamingService streamingService) {
        this.chatService = chatService;
        this.streamingService = streamingService;
    }

    /** 接收问题并返回已创建/复用会话、模型标识及知识来源。 */
    @PostMapping("/preview")
    public ChatResponse preview(Principal principal, @Valid @RequestBody ChatRequest request) {
        return chatService.answer(principal.getName(), request);
    }

    /** POST SSE 允许携带完整问答 Body；重连时复用 requestId 并传 Last-Event-ID。 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            Principal principal,
            @Valid @RequestBody ChatStreamRequest request,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        long cursor = 0;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                cursor = Long.parseLong(lastEventId);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Last-Event-ID 必须是非负整数");
            }
        }
        return streamingService.open(principal.getName(), request, cursor);
    }

    @DeleteMapping("/streams/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Principal principal, @PathVariable String requestId) {
        streamingService.cancel(principal.getName(), requestId);
    }
}
