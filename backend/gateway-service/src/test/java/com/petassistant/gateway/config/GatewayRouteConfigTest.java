package com.petassistant.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 防止实时消息路由在改动业务服务地址后退化成普通 HTTP。 */
class GatewayRouteConfigTest {

    @Test
    void convertsBusinessHttpSchemeToWebSocketScheme() {
        assertThat(GatewayRouteConfig.webSocketUri("http://business-service:8080"))
                .isEqualTo("ws://business-service:8080");
        assertThat(GatewayRouteConfig.webSocketUri("https://api.example.com"))
                .isEqualTo("wss://api.example.com");
    }
}
