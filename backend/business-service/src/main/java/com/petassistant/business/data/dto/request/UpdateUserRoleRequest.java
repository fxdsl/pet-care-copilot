package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 管理员调整用户角色请求。 */
public record UpdateUserRoleRequest(
        @NotBlank(message = "角色不能为空")
        @Pattern(regexp = "USER|VERIFIED_SELLER|MODERATOR|ADMIN", message = "角色值不受支持")
        String role
) {
}
