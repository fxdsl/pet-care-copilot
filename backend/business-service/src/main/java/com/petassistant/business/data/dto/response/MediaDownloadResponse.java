package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 公开媒体的永久 GET 地址；expiresAt 为 null 表示不过期。 */
public record MediaDownloadResponse(String url, Instant expiresAt) { }
