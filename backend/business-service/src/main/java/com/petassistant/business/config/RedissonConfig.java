package com.petassistant.business.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Redisson 客户端配置。普通缓存继续使用 StringRedisTemplate，避免职责混杂。
 */
@Configuration
public class RedissonConfig {

    /**
     * 创建单节点客户端；使用 Lazy 让 Redis 故障不阻止只依赖 MySQL 的请求启动。
     */
    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password
    ) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8)
                .setConnectTimeout(2000)
                .setTimeout(2000);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
