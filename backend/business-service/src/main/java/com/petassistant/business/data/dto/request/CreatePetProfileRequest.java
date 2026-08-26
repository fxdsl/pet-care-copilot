package com.petassistant.business.data.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建宠物档案请求。档案会参与知识过滤和模型个性化上下文构造。
 */
public record CreatePetProfileRequest(
        @NotBlank(message = "宠物名称不能为空")
        @Size(max = 80, message = "宠物名称不能超过 80 个字符")
        String name,
        @NotBlank(message = "宠物类型不能为空")
        @Pattern(regexp = "CAT|DOG|OTHER", message = "宠物类型只能是 CAT、DOG 或 OTHER")
        String petType,
        @Size(max = 100, message = "品种不能超过 100 个字符")
        String breed,
        @Min(value = 0, message = "月龄不能小于 0")
        @Max(value = 600, message = "月龄不能超过 600")
        Integer ageMonths,
        @DecimalMin(value = "0.01", message = "体重必须大于 0")
        @DecimalMax(value = "9999.99", message = "体重超出允许范围")
        BigDecimal weightKg,
        @Size(max = 1000, message = "备注不能超过 1000 个字符")
        String notes
) {
}
