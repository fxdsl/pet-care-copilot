package com.petassistant.business.controller;

import java.util.Map;

import com.petassistant.business.client.AiServiceClient;
import com.petassistant.business.client.SearchIndexClient;
import com.petassistant.business.service.InfrastructureHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聚合业务服务、FastAPI、MySQL 和 Redis 状态的健康检查入口。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    private final AiServiceClient aiServiceClient;
    private final InfrastructureHealthService infrastructureHealthService;
    private final SearchIndexClient searchIndexClient;

    /** 注入外部 AI 健康客户端和本地基础设施检查服务。 */
    public SystemHealthController(
            AiServiceClient aiServiceClient,
            InfrastructureHealthService infrastructureHealthService,
            SearchIndexClient searchIndexClient
    ) {
        this.aiServiceClient = aiServiceClient;
        this.infrastructureHealthService = infrastructureHealthService;
        this.searchIndexClient = searchIndexClient;
    }

    /** 返回每个依赖的独立状态，缓存故障不会掩盖数据库和业务服务状态。 */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "businessService", "UP",
                "aiService", aiServiceClient.isHealthy() ? "UP" : "DOWN",
                "database", infrastructureHealthService.isDatabaseHealthy() ? "UP" : "DOWN",
                "redis", infrastructureHealthService.isRedisHealthy() ? "UP" : "DOWN",
                "rabbitmq", infrastructureHealthService.isRabbitHealthy() ? "UP" : "DOWN",
                "minio", infrastructureHealthService.isMinioHealthy() ? "UP" : "DOWN",
                "opensearch", searchIndexClient.isAvailable() ? "UP" : "DOWN"
        );
    }
}
