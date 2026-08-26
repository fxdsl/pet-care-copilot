package com.petassistant.business.data.dto.response;

/** 注册、登录和刷新成功后的统一令牌响应。 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        CurrentUserResponse user
) {
}
