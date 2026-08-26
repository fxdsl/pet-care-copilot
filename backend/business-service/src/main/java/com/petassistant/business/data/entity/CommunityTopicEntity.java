package com.petassistant.business.data.entity;

import java.time.Instant;

/** 社区话题实体。 */
public record CommunityTopicEntity(
        String id,
        String name,
        String description,
        String status,
        Instant createdAt
) { }
