package com.petassistant.business.service;

import java.time.Instant;

import com.petassistant.business.config.MediaProperties;
import com.petassistant.business.exception.MediaStorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

/** MinIO 协议适配层，业务服务不直接依赖 SDK 参数对象。 */
@Service
public class ObjectStorageService {

    private final MinioClient client;
    private final MediaProperties properties;

    public ObjectStorageService(MinioClient client, MediaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** 创建私有 Bucket 并生成只允许 PUT 指定对象的短期地址。 */
    public PresignedUrl createUploadUrl(String objectKey) {
        try {
            ensureBucket();
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
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
    public StoredObject stat(String objectKey) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
            return new StoredObject(response.size(), response.contentType());
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("无法确认 MinIO 中的媒体对象", exception);
        }
    }

    /** 私有对象只通过一分钟下载地址访问，帖子删除后不能再申请新地址。 */
    public PresignedUrl createDownloadUrl(String objectKey) {
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry((int) properties.getDownloadUrlTtl().toSeconds())
                    .build());
            return new PresignedUrl(url, Instant.now().plus(properties.getDownloadUrlTtl()));
        } catch (Exception exception) {
            throw new MediaStorageUnavailableException("无法生成 MinIO 下载地址", exception);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
    }

    public record PresignedUrl(String url, Instant expiresAt) { }
    public record StoredObject(long sizeBytes, String contentType) { }
}
