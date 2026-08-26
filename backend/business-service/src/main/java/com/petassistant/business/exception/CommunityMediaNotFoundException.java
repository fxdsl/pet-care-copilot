package com.petassistant.business.exception;

/** 媒体记录不存在、未确认或不属于当前用户。 */
public class CommunityMediaNotFoundException extends RuntimeException {
    public CommunityMediaNotFoundException() { super("媒体不存在或无权访问"); }
}
