package com.petassistant.business.exception;

/** 重复举报、并发审核或非法社区关系冲突。 */
public class CommunityInteractionConflictException extends RuntimeException {
    public CommunityInteractionConflictException(String message) {
        super(message);
    }
}
