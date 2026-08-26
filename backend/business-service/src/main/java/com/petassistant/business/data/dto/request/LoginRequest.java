package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户名密码登录请求。 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 32, message = "用户名不能超过 32 个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码不能超过 72 个字符")
        String password
) {
}
