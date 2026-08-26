package com.petassistant.business.exception;

/** 注册用户名已存在。 */
public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException() {
        super("该用户名已被使用，请更换后重试");
    }
}
