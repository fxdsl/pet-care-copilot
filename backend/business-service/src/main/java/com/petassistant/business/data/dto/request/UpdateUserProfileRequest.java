package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.Size;

/** 当前用户资料修改请求；null 表示保持原值，空白可选字段表示清空。 */
public record UpdateUserProfileRequest(
        @Size(max = 100, message = "昵称不能超过 100 个字符")
        String displayName,
        @Size(max = 1000, message = "头像地址不能超过 1000 个字符")
        String avatarUrl,
        @Size(max = 500, message = "个人简介不能超过 500 个字符")
        String bio,
        @Size(max = 100, message = "地区不能超过 100 个字符")
        String region
) {
}
