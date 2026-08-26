package com.petassistant.business.exception;

/** 帖子不存在、已删除或不属于当前用户时统一返回 404。 */
public class CommunityPostNotFoundException extends RuntimeException {
    public CommunityPostNotFoundException() { super("帖子不存在或无权访问"); }
}
