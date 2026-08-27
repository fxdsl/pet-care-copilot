package com.petassistant.business.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建线程安全的 MinIO 客户端，连接在首次对象操作时建立。 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaStorageConfig {

    @Bean
    public MinioClient minioClient(MediaProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .writeTimeout(properties.getReadTimeout())
                .build();
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(httpClient)
                .build();
    }
}
