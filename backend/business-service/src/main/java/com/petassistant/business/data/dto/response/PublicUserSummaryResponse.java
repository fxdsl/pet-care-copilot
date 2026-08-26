package com.petassistant.business.data.dto.response;

/** 公开关系列表中的最小用户卡片。 */
public record PublicUserSummaryResponse(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String region,
        boolean viewerFollowing
) {
}
