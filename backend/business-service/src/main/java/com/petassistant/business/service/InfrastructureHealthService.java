package com.petassistant.business.service;

import java.sql.Connection;

import javax.sql.DataSource;

import com.petassistant.business.config.MediaProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * MySQL 与 Redis 的轻量健康检查服务，不参与业务数据查询。
 */
@Service
public class InfrastructureHealthService {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ConnectionFactory rabbitConnectionFactory;
    private final MinioClient minioClient;
    private final MediaProperties mediaProperties;

    /** 注入标准数据源和 Redis 连接工厂。 */
    public InfrastructureHealthService(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            ConnectionFactory rabbitConnectionFactory,
            MinioClient minioClient,
            MediaProperties mediaProperties
    ) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.minioClient = minioClient;
        this.mediaProperties = mediaProperties;
    }

    /** 使用 JDBC isValid 检查连接，避免为了健康检查继续依赖 JdbcTemplate。 */
    public boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 使用 PING 检查 Redis；失败只影响缓存状态，不影响 MySQL 主业务。 */
    public boolean isRedisHealthy() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** RabbitMQ 连接只用于健康探测，立即关闭，不声明业务资源。 */
    public boolean isRabbitHealthy() {
        org.springframework.amqp.rabbit.connection.Connection connection = null;
        try {
            connection = rabbitConnectionFactory.createConnection();
            return connection.isOpen();
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.close();
        }
    }

    /** MinIO 可连接且目标 Bucket 已存在时视为健康；首次上传会自动创建 Bucket。 */
    public boolean isMinioHealthy() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(mediaProperties.getBucket()).build());
        } catch (Exception ignored) {
            return false;
        }
    }
}
