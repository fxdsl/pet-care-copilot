package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 已确认并关联到帖子的媒体摘要。 */
public record CommunityMediaResponse(
        String id,
        String mediaType,
        String contentType,
        String originalFilename,
        long sizeBytes,
        String status,
        String processingStatus,
        Instant confirmedAt
) { }
