package com.petassistant.business.service;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.security.AuthenticatedUser;
import com.petassistant.business.security.JwtTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 原生 WebSocket 处理器；连接建立后的第一帧必须发送 AUTH，避免把 JWT 放进 URL。 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final JwtTokenService tokenService;
    private final PrincipalSecurityService principalSecurityService;
    private final RealtimeConnectionRegistry registry;

    public RealtimeWebSocketHandler(
            ObjectMapper objectMapper,
            JwtTokenService tokenService,
            PrincipalSecurityService principalSecurityService,
            RealtimeConnectionRegistry registry
    ) {
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
        this.principalSecurityService = principalSecurityService;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        session.sendMessage(new TextMessage("{\"type\":\"AUTH_REQUIRED\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, String> frame = objectMapper.readValue(message.getPayload(), STRING_MAP);
        String authenticatedUserId = registry.userId(session);
        if (authenticatedUserId == null) {
            authenticate(session, frame);
            return;
        }
        if ("PING".equals(frame.get("type"))) {
            registry.touch(authenticatedUserId);
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    private void authenticate(WebSocketSession session, Map<String, String> frame) throws IOException {
        if (!"AUTH".equals(frame.get("type")) || frame.get("token") == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("首帧必须完成认证"));
            return;
        }
        try {
            AuthenticatedUser principal = tokenService.parse(frame.get("token"));
            if (!principalSecurityService.isCurrent(principal)) throw new IllegalArgumentException("令牌权限已失效");
            if (!registry.register(principal.userId(), session)) {
                registry.rejectTooMany(session);
                return;
            }
            session.sendMessage(new TextMessage("{\"type\":\"AUTHENTICATED\"}"));
        } catch (RuntimeException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("访问令牌无效"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(session);
    }
}
