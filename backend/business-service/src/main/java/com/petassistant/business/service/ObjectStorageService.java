package com.petassistant.business.service;

import java.time.Instant;
import java.util.Arrays;

import com.petassistant.business.config.MediaProperties;
import com.petassistant.business.exception.MediaStorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** MinIO 协议适配层，业务服务不直接依赖 SDK 参数对象。 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final MinioClient client;
    private final MinioClient publicClient;
    private final MediaProperties properties;
    private volatile boolean publicPolicyReady;

    public ObjectStorageService(
            MinioClient client,
            @Qualifier("publicMinioClient") MinioClient publicClient,
            MediaProperties properties
    ) {
        this.client = client;
        this.publicClient = publicClient;
        this.properties = properties;
    }

    /** 启动时尽力把已有 Bucket 调整为公开只读；失败不会阻止业务服务启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePublicBucket() {
        try {
            ensurePublicBucket();
        } catch (Exception exception) {
            log.warn("Unable to initialize public MinIO bucket; the next upload will retry", exception);
        }
    }

    /** 创建公开只读 Bucket，并生成只允许 PUT 指定对象的短期上传地址。 */
    @Retry(name = "minio")
    @CircuitBreaker(name = "minio")
    @Bulkhead(name = "minio", type = Bulkhead.Type.SEMAPHORE)
    public PresignedUrl createUploadUrl(String objectKey) {
        try {
            ensurePublicBucket();
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry((int) properties.getUploadUrlTtl().toSeconds())
                    .build());
            return new PresignedUrl(url, Instant.now().plus(properties.getUploadUrlTtl()));
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("无法生成 MinIO 上传地址", exception);
        }
    }

    /** 确认阶段从对象存储读取真实大小，不能相信浏览器再次提交的大小。 */
    @Retry(name = "minio")
    @CircuitBreaker(name = "minio")
    @Bulkhead(name = "minio", type = Bulkhead.Type.SEMAPHORE)
    public StoredObject stat(String objectKey) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            return new StoredObject(response.size(), response.contentType());
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("无法确认 MinIO 中的媒体对象", exception);
        }
    }

    /** 公开 Bucket 的永久对象地址，不包含签名和过期时间。 */
    public String createPublicUrl(String objectKey) {
        HttpUrl.Builder url = HttpUrl.get(properties.getPublicEndpoint()).newBuilder()
                .addPathSegment(properties.getBucket());
        Arrays.stream(objectKey.split("/"))
                .filter(segment -> !segment.isBlank())
                .forEach(url::addPathSegment);
        return url.build().toString();
    }

    private synchronized void ensurePublicBucket() throws Exception {
        if (publicPolicyReady) return;
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(properties.getBucket())
                .config(publicReadPolicy(properties.getBucket()))
                .build());
        publicPolicyReady = true;
    }

    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
    }

    public record PresignedUrl(String url, Instant expiresAt) { }
    public record StoredObject(long sizeBytes, String contentType) { }
}
