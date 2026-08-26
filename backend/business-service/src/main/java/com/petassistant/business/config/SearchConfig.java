package com.petassistant.business.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/** 统一搜索 HTTP 客户端配置；OpenSearch 故障由业务层显式降级到 MySQL。 */
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    @Bean
    RestClient searchRestClient(SearchProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.endpoint())
                .requestFactory(requestFactory)
                // 使用 Spring Boot 已配置的 ObjectMapper，确保 Instant 按 ISO-8601 输出，
                // 避免默认转换器生成 OpenSearch date 无法解析的小数秒时间戳。
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                })
                .build();
    }
}
