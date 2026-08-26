package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 使用一次性旋转刷新令牌换取新令牌对。 */
public record RefreshTokenRequest(
        @NotBlank(message = "刷新令牌不能为空")
        @Size(max = 300, message = "刷新令牌格式无效")
        String refreshToken
) {
}
