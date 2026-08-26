package com.petassistant.business.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
/**
 * FastAPI 服务地址与超时配置，避免客户端在代码中写死环境信息。
 */
public record AiServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration responseTimeout
) {
}
