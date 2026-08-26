package com.petassistant.business.exception;

/** 用户名、密码或刷新令牌验证失败；对外统一提示，避免账号枚举。 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("用户名、密码或登录凭证不正确");
    }
}
