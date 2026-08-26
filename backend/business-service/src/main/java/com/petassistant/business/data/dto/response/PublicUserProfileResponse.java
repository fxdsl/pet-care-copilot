package com.petassistant.business.data.dto.response;

import java.time.Instant;
import java.util.List;

/** 他人主页公开资料；不包含密码、安全版本、登录时间或宠物备注。 */
public record PublicUserProfileResponse(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String region,
        Instant joinedAt,
        long postCount,
        long followerCount,
        long followingCount,
        boolean viewerFollowing,
        boolean ownProfile,
        List<PetSummary> pets
) {
    /** 公开宠物摘要只用于主页展示，不作为他人 AI 问答上下文。 */
    public record PetSummary(String id, String name, String petType, String breed, Integer ageMonths) {
    }
}
