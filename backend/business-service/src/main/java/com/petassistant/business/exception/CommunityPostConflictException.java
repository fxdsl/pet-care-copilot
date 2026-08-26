package com.petassistant.business.exception;

/** 帖子版本已变化时阻止旧页面覆盖新内容。 */
public class CommunityPostConflictException extends RuntimeException {
    public CommunityPostConflictException() { super("帖子已被其他请求更新，请刷新后重试"); }
}
