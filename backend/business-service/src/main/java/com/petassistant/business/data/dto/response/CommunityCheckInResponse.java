package com.petassistant.business.data.dto.response;

import java.time.LocalDate;

/** 每日养宠打卡响应；MySQL 为事实，Redis Bitmap 为快速月视图副本。 */
public record CommunityCheckInResponse(
        LocalDate date,
        boolean checkedIn,
        int daysThisMonth,
        int currentStreak
) { }
