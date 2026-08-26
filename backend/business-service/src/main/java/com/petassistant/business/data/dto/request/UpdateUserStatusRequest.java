package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 管理员启用或禁用用户请求；LOCKED 仍由安全策略管理，不开放手工设置。 */
public record UpdateUserStatusRequest(
        @NotBlank(message = "账号状态不能为空")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "账号状态只允许 ACTIVE 或 DISABLED")
        String status
) {
}
