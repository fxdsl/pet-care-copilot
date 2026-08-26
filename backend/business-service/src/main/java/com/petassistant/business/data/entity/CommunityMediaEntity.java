package com.petassistant.business.data.entity;

import java.time.Instant;

/** MinIO 对象的业务归属和确认状态，数据库不保存二进制正文。 */
public record CommunityMediaEntity(
        String id,
        String ownerId,
        String postId,
        String objectKey,
        String originalFilename,
        String mediaType,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        String processingStatus,
        Instant createdAt,
        Instant confirmedAt
) { }
