package com.petassistant.business.exception;

/** JWT 指向的用户已被删除或停用。 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("当前用户不存在或已被停用");
    }
}
