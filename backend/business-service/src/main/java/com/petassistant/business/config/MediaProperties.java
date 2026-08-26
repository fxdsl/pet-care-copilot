package com.petassistant.business.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MinIO 私有 Bucket、预签名时效和媒体大小限制。 */
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "petassistant";
    private String secretKey = "petassistant_dev_secret";
    private String bucket = "pet-community";
    private Duration uploadUrlTtl = Duration.ofMinutes(10);
    private Duration downloadUrlTtl = Duration.ofMinutes(1);
    private long maxImageBytes = 10 * 1024 * 1024;
    private long maxVideoBytes = 50 * 1024 * 1024;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public Duration getDownloadUrlTtl() { return downloadUrlTtl; }
    public void setDownloadUrlTtl(Duration downloadUrlTtl) { this.downloadUrlTtl = downloadUrlTtl; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long maxVideoBytes) { this.maxVideoBytes = maxVideoBytes; }
}
