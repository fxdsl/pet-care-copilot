package com.petassistant.business.data.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 宠物档案数据库实体，与 {@code pet_profile} 表一一对应。
 */
public record PetProfileEntity(
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
