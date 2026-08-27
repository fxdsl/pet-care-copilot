package com.petassistant.gateway.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** Gateway 路由与限流键配置；Authorization 头默认原样透传，由业务服务最终验签。 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RedisRateLimiter apiRedisRateLimiter(
            @Value("${app.rate-limit.replenish-rate:20}") int replenishRate,
            @Value("${app.rate-limit.burst-capacity:40}") int burstCapacity
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }

    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                // 不解析、不记录 JWT；只使用不可逆摘要区分调用方，真正权限仍由业务服务校验。
                return Mono.just("token:" + sha256(authorization.substring(7)));
            }
            // Nginx 会覆盖而不是追加 X-Forwarded-For；因此这里可取得真实客户端地址，
            // 又不会让公网请求伪造任意限流 Key。Gateway 本身仍只能部署在内网。
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            var address = exchange.getRequest().getRemoteAddress();
            String clientAddress = forwarded == null || forwarded.isBlank()
                    ? (address == null ? "unknown" : address.getAddress().getHostAddress())
                    : forwarded.split(",", 2)[0].trim();
            return Mono.just("ip:" + sha256(clientAddress));
        };
    }

    @Bean
    public RouteLocator businessRoutes(
            RouteLocatorBuilder builder,
            RedisRateLimiter apiRedisRateLimiter,
            KeyResolver userOrIpKeyResolver,
            @Value("${app.business-service-uri:http://localhost:8080}") String businessServiceUri
    ) {
        return builder.routes()
                .route("business-api", route -> route.path("/api/**")
                        .filters(filters -> filters
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(apiRedisRateLimiter)
                                        .setKeyResolver(userOrIpKeyResolver))
                                .addResponseHeader("X-Gateway", "pet-assistant"))
                        .uri(businessServiceUri))
                // WebSocket 必须使用 ws/wss 路由协议，否则 HTTP API 正常时实时消息仍可能升级失败。
                .route("business-websocket", route -> route.path("/ws/**")
                        .filters(filters -> filters
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(apiRedisRateLimiter)
                                        .setKeyResolver(userOrIpKeyResolver))
                                .addResponseHeader("X-Gateway", "pet-assistant"))
                        .uri(webSocketUri(businessServiceUri)))
                .build();
    }

    static String webSocketUri(String httpUri) {
        if (httpUri.startsWith("https://")) return "wss://" + httpUri.substring(8);
        if (httpUri.startsWith("http://")) return "ws://" + httpUri.substring(7);
        return httpUri;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception impossible) {
            throw new IllegalStateException("JVM 不支持 SHA-256", impossible);
        }
    }
}
