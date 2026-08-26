package com.petassistant.business.exception;

/** 登录、注册或其他敏感入口触发 Redis 频率限制。 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
