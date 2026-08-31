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

    /**
     * 接收问题并返回流式问答结果。
     */

    /** POST SSE 允许携带完整问答 Body；重连时复用 requestId 并传 Last-Event-ID。 */
    //响应类型: TEXT_EVENT_STREAM_VALUE - SSE 流式输出格式
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    //Spring 提供的 SSE（Server-Sent Events）发送器
    public SseEmitter stream(
            Principal principal,
            @Valid @RequestBody ChatStreamRequest request,
            //SSE 重连机制的事件ID，用于断点续传
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        //断点续传机制。
        //初始化游标: 默认 cursor = 0（从头开始）
        long cursor = 0;
        //检查重连请求: 如果客户端携带了 Last-Event-ID 请求头，尝试解析为游标
        //如果解析失败，抛出 IllegalArgumentException 异常
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                cursor = Long.parseLong(lastEventId);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Last-Event-ID 必须是非负整数");
            }
        }
        //如果确实为断点重连，调用流式服务: 将 cursor 传递给 streamingService.open() 实现从指定位置恢复数据流
        //否则，从头开始发送数据流
        return streamingService.open(principal.getName(), request, cursor);
    }

    /** DELETE 取消流式问答，返回 NO_CONTENT。 */
    @DeleteMapping("/streams/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Principal principal, @PathVariable String requestId) {
        streamingService.cancel(principal.getName(), requestId);
    }
}
