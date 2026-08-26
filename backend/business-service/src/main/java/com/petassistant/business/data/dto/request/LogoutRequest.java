package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 退出请求只撤销当前刷新令牌；短期访问令牌到期后自然失效。 */
public record LogoutRequest(
        @NotBlank(message = "刷新令牌不能为空")
        @Size(max = 300, message = "刷新令牌格式无效")
        String refreshToken
) {
}
