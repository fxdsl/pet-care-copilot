package com.petassistant.business.security;

import java.security.Principal;

/**
 * Spring Security 中的最小登录主体，不包含密码、档案正文等敏感数据。
 */
public record AuthenticatedUser(
        String userId,
        String username,
        String role,
        long securityVersion
) implements Principal {

    /** Authentication#getName() 统一返回数据库用户 ID，供 Controller 做所有权过滤。 */
    @Override
    public String getName() {
        return userId;
    }
}
