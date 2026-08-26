package com.petassistant.business.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * FastAPI HTTP 客户端配置。
 * 使用基于 HttpURLConnection 的 HTTP/1.1 客户端，避免 Uvicorn 与 h2c 升级不兼容。
 */
@Configuration
@EnableConfigurationProperties({AiServiceProperties.class, AgentProperties.class})
public class AiClientConfig {

    /**
     * 创建全局复用的同步 RestClient；当前 Spring MVC 服务无需混用响应式 WebClient。
     */
    @Bean
    RestClient aiRestClient(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.responseTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
