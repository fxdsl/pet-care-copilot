package com.petassistant.business.controller;

import java.security.Principal;
import java.util.List;

import com.petassistant.business.data.dto.request.CreateConversationRequest;
import com.petassistant.business.data.dto.request.CreateMessageRequest;
import com.petassistant.business.data.dto.response.ConversationResponse;
import com.petassistant.business.data.dto.response.MessageResponse;
import com.petassistant.business.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话与消息 HTTP 控制器，所有数据库和缓存操作都委托给 Service。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService service;

    /** 注入会话业务服务。 */
    public ConversationController(ConversationService service) {
        this.service = service;
    }

    /** 使用 JWT 中的当前用户创建会话，浏览器不能指定 userId。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(Principal principal, @Valid @RequestBody CreateConversationRequest request) {
        return service.create(principal.getName(), request);
    }

    /** 查询最近会话，Service 会把数量限制在 1 到 100。 */
    @GetMapping
    public List<ConversationResponse> list(Principal principal, @RequestParam(defaultValue = "20") int limit) {
        return service.list(principal.getName(), limit);
    }

    /** 新增消息并在事务提交后使旧上下文缓存失效。 */
    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse addMessage(
            Principal principal,
            @PathVariable String conversationId,
            @Valid @RequestBody CreateMessageRequest request
    ) {
        // 浏览器只能追加自己的 USER 消息；ASSISTANT/SYSTEM/TOOL 角色只允许服务内部生成。
        if (!"USER".equals(request.role())) {
            throw new IllegalArgumentException("外部接口只能添加 USER 角色消息");
        }
        return service.addMessage(principal.getName(), conversationId, request);
    }

    /** 按创建顺序查询指定会话的消息。 */
    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> getMessages(Principal principal, @PathVariable String conversationId) {
        return service.getMessages(principal.getName(), conversationId);
    }
}
