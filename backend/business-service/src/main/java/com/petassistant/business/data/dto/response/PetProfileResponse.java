package com.petassistant.business.data.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 宠物档案响应，供前端选择问答上下文。
 */
public record PetProfileResponse(
        String id,
        String userId,
        String name,
        String petType,
        String breed,
        Integer ageMonths,
        BigDecimal weightKg,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
