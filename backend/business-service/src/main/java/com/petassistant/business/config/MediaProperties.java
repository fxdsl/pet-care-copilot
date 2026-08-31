package com.petassistant.business.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO 公开只读 Bucket、上传签名时效和媒体大小限制。 */
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "";
    private String accessKey = "petassistant";
    private String secretKey = "petassistant_dev_secret";
    private String bucket = "pet-community";
    private Duration uploadUrlTtl = Duration.ofMinutes(10);
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private long maxImageBytes = 10 * 1024 * 1024;
    private long maxVideoBytes = 50 * 1024 * 1024;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    /** 浏览器访问地址；未单独配置时兼容本机开发，沿用内部地址。 */
    public String getPublicEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long maxVideoBytes) { this.maxVideoBytes = maxVideoBytes; }
}
