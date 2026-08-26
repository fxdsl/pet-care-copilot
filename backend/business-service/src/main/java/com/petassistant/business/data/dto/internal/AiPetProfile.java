package com.petassistant.business.data.dto.internal;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发送给 FastAPI 的最小宠物档案上下文，不包含数据库时间等无关信息。
 */
public record AiPetProfile(
        String name,
        @JsonProperty("pet_type") String petType,
        String breed,
        @JsonProperty("age_months") Integer ageMonths,
        @JsonProperty("weight_kg") BigDecimal weightKg,
        String notes
) {
}
