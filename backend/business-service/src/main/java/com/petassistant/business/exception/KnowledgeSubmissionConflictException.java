package com.petassistant.business.exception;

/** 投稿状态或版本已变化时返回稳定的 409 契约。 */
public class KnowledgeSubmissionConflictException extends RuntimeException {
    public KnowledgeSubmissionConflictException(String message) {
        super(message);
    }
}
