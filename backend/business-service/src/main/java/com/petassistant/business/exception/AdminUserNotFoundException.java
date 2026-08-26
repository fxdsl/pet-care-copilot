package com.petassistant.business.exception;

/** 管理后台查询的目标用户不存在；与当前 JWT 主体失效的 401 明确区分。 */
public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException() {
        super("目标用户不存在，请刷新用户列表");
    }
}
