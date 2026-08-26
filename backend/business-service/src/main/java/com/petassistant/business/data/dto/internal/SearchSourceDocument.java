package com.petassistant.business.data.dto.internal;

import java.time.Instant;

/** 从 MySQL 安全读取、写入 OpenSearch 的最小公开搜索投影。 */
public record SearchSourceDocument(
        String id,
        String documentType,
        String title,
        String content,
        String authorName,
        String avatarUrl,
        String sourceUrl,
        String petType,
        String category,
        String trustLevel,
        Instant publishedAt,
        String routePath
) { }
