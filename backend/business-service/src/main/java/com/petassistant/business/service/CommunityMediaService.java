package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.petassistant.business.config.MediaProperties;
import com.petassistant.business.data.dto.request.CreateMediaUploadRequest;
import com.petassistant.business.data.dto.response.CommunityMediaResponse;
import com.petassistant.business.data.dto.response.MediaDownloadResponse;
import com.petassistant.business.data.dto.response.MediaUploadResponse;
import com.petassistant.business.data.entity.CommunityMediaEntity;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import com.petassistant.business.exception.CommunityMediaNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 社区媒体申请、MinIO 确认、归属绑定和私有下载授权。 */
@Service
public class CommunityMediaService {

    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> VIDEO_TYPES = List.of("video/mp4", "video/webm");

    private final CommunityMediaMapper mapper;
    private final ObjectStorageService objectStorage;
    private final MediaProperties properties;
    private final OutboxService outboxService;

    public CommunityMediaService(
            CommunityMediaMapper mapper,
            ObjectStorageService objectStorage,
            MediaProperties properties,
            OutboxService outboxService
    ) {
        this.mapper = mapper;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.outboxService = outboxService;
    }

    /** 先记录所有者和预期元数据，再返回 PUT 地址，防止对象脱离业务归属。 */
    @Transactional
    public MediaUploadResponse createUpload(String userId, CreateMediaUploadRequest request) {
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        String mediaType = resolveMediaType(contentType, request.sizeBytes());
        String mediaId = UUID.randomUUID().toString();
        String objectKey = "community/" + userId + "/" + mediaId + safeExtension(request.fileName());
        ObjectStorageService.PresignedUrl upload = objectStorage.createUploadUrl(objectKey);
        Instant now = Instant.now();
        mapper.insert(new CommunityMediaEntity(
                mediaId, userId, null, objectKey, request.fileName().trim(), mediaType,
                contentType, request.sizeBytes(), normalizeChecksum(request.checksumSha256()),
                "PENDING", "WAITING", now, null
        ));
        return new MediaUploadResponse(mediaId, objectKey, upload.url(), "PUT", upload.expiresAt());
    }

    /** 上传完成后以 MinIO 的实际大小为准确认，并同事务写 MEDIA_CONFIRMED 事件。 */
    @Transactional
    public CommunityMediaResponse confirm(String userId, String mediaId) {
        CommunityMediaEntity media = requireOwned(userId, mediaId);
        if ("CONFIRMED".equals(media.status())) return toResponse(media);
        ObjectStorageService.StoredObject stored = objectStorage.stat(media.objectKey());
        if (stored.sizeBytes() != media.sizeBytes()) {
            throw new IllegalArgumentException("媒体实际大小与申请记录不一致，请重新上传");
        }
        if (stored.contentType() != null && !stored.contentType().isBlank()
                && !media.contentType().equalsIgnoreCase(stored.contentType())) {
            throw new IllegalArgumentException("媒体实际 Content-Type 与申请记录不一致");
        }
        if (mapper.markConfirmed(mediaId, userId, Instant.now()) == 0) throw new CommunityMediaNotFoundException();
        outboxService.record("COMMUNITY_MEDIA", mediaId, "MEDIA_CONFIRMED", userId);
        return toResponse(requireOwned(userId, mediaId));
    }

    /** 只有对象所有者或已发布帖子读者可以申请短期下载地址。 */
    @Transactional(readOnly = true)
    public MediaDownloadResponse download(String userId, String mediaId) {
        CommunityMediaEntity media = mapper.findAccessible(mediaId, userId);
        if (media == null) throw new CommunityMediaNotFoundException();
        ObjectStorageService.PresignedUrl url = objectStorage.createDownloadUrl(media.objectKey());
        return new MediaDownloadResponse(url.url(), url.expiresAt());
    }

    public CommunityMediaEntity requireOwned(String userId, String mediaId) {
        CommunityMediaEntity media = mapper.findOwned(mediaId, userId);
        if (media == null) throw new CommunityMediaNotFoundException();
        return media;
    }

    private String resolveMediaType(String contentType, long sizeBytes) {
        if (IMAGE_TYPES.contains(contentType)) {
            if (sizeBytes > properties.getMaxImageBytes()) throw new IllegalArgumentException("图片不能超过 10 MiB");
            return "IMAGE";
        }
        if (VIDEO_TYPES.contains(contentType)) {
            if (sizeBytes > properties.getMaxVideoBytes()) throw new IllegalArgumentException("视频不能超过 50 MiB");
            return "VIDEO";
        }
        throw new IllegalArgumentException("只支持 JPEG、PNG、WebP、MP4 或 WebM 媒体");
    }

    private static CommunityMediaResponse toResponse(CommunityMediaEntity media) {
        return new CommunityMediaResponse(
                media.id(), media.mediaType(), media.contentType(), media.originalFilename(),
                media.sizeBytes(), media.status(), media.processingStatus(), media.confirmedAt()
        );
    }

    private static String safeExtension(String fileName) {
        String normalized = fileName.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot < 0) return "";
        String extension = normalized.substring(dot);
        return extension.matches("\\.(jpg|jpeg|png|webp|mp4|webm)") ? extension : "";
    }

    private static String normalizeChecksum(String checksum) {
        return checksum == null || checksum.isBlank() ? null : checksum.trim().toLowerCase(Locale.ROOT);
    }
}
