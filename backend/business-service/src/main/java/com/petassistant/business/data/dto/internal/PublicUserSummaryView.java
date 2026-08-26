package com.petassistant.business.data.dto.internal;

/** 关注/粉丝分页的公开用户投影。 */
public record PublicUserSummaryView(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String region,
        boolean viewerFollowing
) {
}
