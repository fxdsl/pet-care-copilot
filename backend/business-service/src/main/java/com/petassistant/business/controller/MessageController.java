package com.petassistant.business.controller;

import java.security.Principal;

import com.petassistant.business.data.dto.request.SendDirectMessageRequest;
import com.petassistant.business.data.dto.response.DirectConversationPageResponse;
import com.petassistant.business.data.dto.response.DirectMessagePageResponse;
import com.petassistant.business.data.dto.response.DirectMessageResponse;
import com.petassistant.business.data.dto.response.MessageUnreadResponse;
import com.petassistant.business.data.dto.response.NotificationPageResponse;
import com.petassistant.business.data.dto.response.NotificationResponse;
import com.petassistant.business.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 普通用户消息中心接口；所有查询都从 Principal 获取当前用户，禁止传入 ownerId。 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    /**
     * 获取对应类型的用户通知列表
     * @param principal
     * @param type
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/notifications")
    public NotificationPageResponse notifications(
            Principal principal,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.notifications(principal.getName(), type, page, size);
    }

    /**
     * 标记通知为已读
     * @param principal
     * @param notificationId
     * @return
     */
    @PutMapping("/notifications/{notificationId}/read")
    public NotificationResponse markRead(Principal principal, @PathVariable String notificationId) {
        return service.markNotificationRead(principal.getName(), notificationId);
    }

    /**
     * 标记所有通知为已读
     * @param principal
     * @param type
     */
    @PutMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Principal principal, @RequestParam(required = false) String type) {
        service.markAllNotificationsRead(principal.getName(), type);
    }

    /**
     * 获取用户未读消息数量
     * @param principal
     * @return
     */
    @GetMapping("/unread")
    public MessageUnreadResponse unread(Principal principal) {
        return service.unread(principal.getName());
    }

    /**
     * 获取私信对话列表
     * @param principal
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/conversations")
    public DirectConversationPageResponse conversations(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return service.conversations(principal.getName(), page, size);
    }
    /**
     * 获取私信对话详情
     * @param principal
     * @param conversationId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/conversations/{conversationId}")
    public DirectMessagePageResponse directMessages(
            Principal principal,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return service.directMessages(principal.getName(), conversationId, page, size);
    }

    /**
     * 发送私信
     * @param principal
     * @param request
     * @return
     */
    @PostMapping("/direct")
    //强制返回的状态码为201，前端需要根据状态码判断下一步操作，比如返回201后刷新消息列表
    @ResponseStatus(HttpStatus.CREATED)
    public DirectMessageResponse send(
            Principal principal,
            @Valid @RequestBody SendDirectMessageRequest request
    ) {
        return service.send(principal.getName(), request);
    }
}
