package com.petassistant.business.exception;

/** Redis 无法保存或旋转刷新令牌时使用，禁止伪造无缓存登录态。 */
public class AuthSessionUnavailableException extends RuntimeException {
    public AuthSessionUnavailableException(Throwable cause) {
        super("登录会话服务暂时不可用", cause);
    }
}
