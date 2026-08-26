package com.petassistant.business.exception;

/**
 * 请求的会话编号在 MySQL 中不存在。
 */
public class ConversationNotFoundException extends RuntimeException {

    /** 使用会话编号构造便于排查的错误信息。 */
    public ConversationNotFoundException(String conversationId) {
        super("会话不存在：" + conversationId);
    }
}
