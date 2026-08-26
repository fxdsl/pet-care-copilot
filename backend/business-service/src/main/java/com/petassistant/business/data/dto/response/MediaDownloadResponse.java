package com.petassistant.business.data.dto.response;

import java.time.Instant;

/** 私有媒体的短期 GET 地址。 */
public record MediaDownloadResponse(String url, Instant expiresAt) { }
