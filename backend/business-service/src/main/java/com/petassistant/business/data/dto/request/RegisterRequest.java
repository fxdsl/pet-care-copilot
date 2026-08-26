package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户注册请求；密码只在内存中短暂存在，落库前使用 BCrypt。 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "用户名只能包含 4～32 位字母、数字或下划线")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为 8～72 个字符")
        String password,
        @Size(max = 100, message = "昵称不能超过 100 个字符")
        String displayName
) {
}
