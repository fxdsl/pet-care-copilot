package com.petassistant.business.exception;

/** 投稿不存在或普通用户无权读取时统一返回 404，避免泄露他人草稿。 */
public class KnowledgeSubmissionNotFoundException extends RuntimeException {
    public KnowledgeSubmissionNotFoundException() {
        super("知识投稿不存在或当前账号无权访问");
    }
}
