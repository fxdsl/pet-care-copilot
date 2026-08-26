package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 浏览器直传 MinIO 所需的一次性业务记录和短期 PUT 地址。 */
public record MediaUploadResponse(
        String mediaId,
        String objectKey,
        String uploadUrl,
        String httpMethod,
        Instant expiresAt
) { }
