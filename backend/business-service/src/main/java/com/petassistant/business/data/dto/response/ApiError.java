package com.petassistant.business.data.dto.response;

import java.time.Instant;

/**
 * 全局统一错误响应，便于前端按 code 区分错误类型。
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp
) {
}
