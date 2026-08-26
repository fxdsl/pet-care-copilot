package com.petassistant.business.service;

import java.time.Instant;

import com.petassistant.business.data.dto.request.UpdateUserRoleRequest;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.AdminAuditMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.AdminOperationConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理员不能撤销自身权限，避免把当前管理入口直接锁死。 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserMapper userMapper;
    @Mock AdminAuditMapper auditMapper;
    @Mock RefreshTokenService refreshTokenService;
    @Mock UserSessionCacheService userCache;
    @Mock PrincipalSecurityService principalSecurityService;
    @Mock OutboxService outboxService;

    @Test
    void shouldRejectSelfDemotion() {
        AdminUserService service = new AdminUserService(
                userMapper, auditMapper, refreshTokenService, userCache, principalSecurityService, outboxService
        );
        when(userMapper.findById("admin-1")).thenReturn(user("admin-1", "ADMIN", "ACTIVE", 1));

        assertThatThrownBy(() -> service.updateRole(
                "admin-1", "admin-1", new UpdateUserRoleRequest("USER")
        )).isInstanceOf(AdminOperationConflictException.class);

        verify(userMapper, never()).updateRole(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static UserEntity user(String id, String role, String status, long securityVersion) {
        Instant now = Instant.now();
        return new UserEntity(
                id, id, "hash", id, role, status, securityVersion,
                null, null, null, null, now, now
        );
    }
}
