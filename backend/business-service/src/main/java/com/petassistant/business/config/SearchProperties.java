package com.petassistant.business.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenSearch、搜索缓存和异步索引同步配置。 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        boolean enabled,
        String endpoint,
        String indexName,
        int embeddingDimensions,
        Duration connectTimeout,
        Duration readTimeout,
        Duration resultCacheTtl,
        boolean rabbitListenerEnabled,
        String rabbitExchange,
        String rabbitQueue,
        String rabbitRoutingKey
) { }
