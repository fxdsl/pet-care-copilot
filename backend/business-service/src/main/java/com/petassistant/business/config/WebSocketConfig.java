package com.petassistant.business.config;

import com.petassistant.business.service.RealtimeWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 浏览器实时连接入口；业务鉴权由 WebSocket 首帧完成。 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final String allowedOrigin;

    public WebSocketConfig(
            RealtimeWebSocketHandler handler,
            @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin
    ) {
        this.handler = handler;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/realtime").setAllowedOrigins(allowedOrigin);
    }
}
