package com.petassistant.business.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 创建线程安全的 MinIO 客户端，连接在首次对象操作时建立。 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaStorageConfig {

    @Bean
    @Primary
    public MinioClient minioClient(MediaProperties properties) {
        return createClient(properties.getEndpoint(), properties);
    }

    /** 只用于生成浏览器可访问的预签名上传地址，不承担容器内部读写。 */
    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(MediaProperties properties) {
        return createClient(properties.getPublicEndpoint(), properties);
    }

    private MinioClient createClient(String endpoint, MediaProperties properties) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .writeTimeout(properties.getReadTimeout())
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .region("us-east-1")
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(httpClient)
                .build();
    }
}
