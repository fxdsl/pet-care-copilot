package com.petassistant.business.exception;

/**
 * 前端提交了不存在的宠物档案编号时抛出的业务异常。
 */
public class PetProfileNotFoundException extends RuntimeException {

    /** 使用档案编号构造可直接展示的错误信息。 */
    public PetProfileNotFoundException(String profileId) {
        super("宠物档案不存在：" + profileId);
    }
}
