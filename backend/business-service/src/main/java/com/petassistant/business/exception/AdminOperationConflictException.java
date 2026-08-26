package com.petassistant.business.exception;

/** 管理操作会导致自我锁定或系统失去最后一个管理员时返回冲突。 */
public class AdminOperationConflictException extends RuntimeException {

    public AdminOperationConflictException(String message) {
        super(message);
    }
}
