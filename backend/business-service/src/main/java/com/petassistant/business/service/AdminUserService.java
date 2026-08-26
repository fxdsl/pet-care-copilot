package com.petassistant.business.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.petassistant.business.data.dto.request.UpdateUserRoleRequest;
import com.petassistant.business.data.dto.request.UpdateUserStatusRequest;
import com.petassistant.business.data.dto.response.AdminAuditPageResponse;
import com.petassistant.business.data.dto.response.AdminUserPageResponse;
import com.petassistant.business.data.dto.response.AdminUserResponse;
import com.petassistant.business.data.entity.AdminAuditEntity;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.AdminAuditMapper;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.AdminOperationConflictException;
import com.petassistant.business.exception.AdminUserNotFoundException;
import com.petassistant.business.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 管理员用户治理：查询、角色/状态变更、最后管理员保护和审计。 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final List<String> ROLES = List.of("USER", "VERIFIED_SELLER", "MODERATOR", "ADMIN");
    private static final List<String> STATUSES = List.of("ACTIVE", "DISABLED");

    private final UserMapper userMapper;
    private final AdminAuditMapper auditMapper;
    private final RefreshTokenService refreshTokenService;
    private final UserSessionCacheService userCache;
    private final PrincipalSecurityService principalSecurityService;
    private final OutboxService outboxService;

    public AdminUserService(
            UserMapper userMapper,
            AdminAuditMapper auditMapper,
            RefreshTokenService refreshTokenService,
            UserSessionCacheService userCache,
            PrincipalSecurityService principalSecurityService,
            OutboxService outboxService
    ) {
        this.userMapper = userMapper;
        this.auditMapper = auditMapper;
        this.refreshTokenService = refreshTokenService;
        this.userCache = userCache;
        this.principalSecurityService = principalSecurityService;
        this.outboxService = outboxService;
    }

    /** 条件为空时查询全部用户，页大小强制限制为 1～100。 */
    @Transactional(readOnly = true)
    public AdminUserPageResponse list(String keyword, String role, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String safeRole = normalizeOptional(role);
        String safeStatus = normalizeOptional(status);
        if (safeRole != null && !ROLES.contains(safeRole)) throw new IllegalArgumentException("角色筛选值不受支持");
        if (safeStatus != null && !List.of("ACTIVE", "DISABLED", "LOCKED").contains(safeStatus)) {
            throw new IllegalArgumentException("状态筛选值不受支持");
        }
        String safeKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<AdminUserResponse> items = userMapper.findPage(
                safeKeyword, safeRole, safeStatus, safePage * safeSize, safeSize
        ).stream().map(AdminUserService::toResponse).toList();
        return new AdminUserPageResponse(
                items, safePage, safeSize, userMapper.countPage(safeKeyword, safeRole, safeStatus)
        );
    }

    /** 角色调整与审计在同一 MySQL 事务中提交。 */
    @Transactional
    public AdminUserResponse updateRole(String actorId, String targetId, UpdateUserRoleRequest request) {
        UserEntity actor = requireAdmin(actorId);
        UserEntity target = requireUser(targetId);
        String nextRole = request.role().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(nextRole)) throw new IllegalArgumentException("角色值不受支持");
        if (actor.id().equals(target.id()) && !"ADMIN".equals(nextRole)) {
            throw new AdminOperationConflictException("不能撤销当前登录账号自己的管理员权限");
        }
        protectLastAdmin(target, nextRole, target.status());
        if (target.role().equals(nextRole)) return toResponse(target);
        userMapper.updateRole(target.id(), nextRole, Instant.now());
        audit(actor.id(), target.id(), "USER_ROLE_CHANGED", target.role(), nextRole);
        outboxService.record("SEARCH_DOCUMENT", target.id(), "SEARCH_USER_UPSERT", actor.id());
        invalidateAfterCommit(target.id());
        return toResponse(requireUser(target.id()));
    }

    /** 禁用账号会使全部旧访问令牌和刷新令牌失效。 */
    @Transactional
    public AdminUserResponse updateStatus(String actorId, String targetId, UpdateUserStatusRequest request) {
        UserEntity actor = requireAdmin(actorId);
        UserEntity target = requireUser(targetId);
        String nextStatus = request.status().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(nextStatus)) throw new IllegalArgumentException("账号状态值不受支持");
        if (actor.id().equals(target.id()) && !"ACTIVE".equals(nextStatus)) {
            throw new AdminOperationConflictException("不能禁用当前登录的管理员账号");
        }
        protectLastAdmin(target, target.role(), nextStatus);
        if (target.status().equals(nextStatus)) return toResponse(target);
        userMapper.updateStatus(target.id(), nextStatus, Instant.now());
        audit(actor.id(), target.id(), "USER_STATUS_CHANGED", target.status(), nextStatus);
        outboxService.record("SEARCH_DOCUMENT", target.id(), "SEARCH_USER_UPSERT", actor.id());
        invalidateAfterCommit(target.id());
        return toResponse(requireUser(target.id()));
    }

    @Transactional(readOnly = true)
    public AdminAuditPageResponse audits(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new AdminAuditPageResponse(
                auditMapper.findRecent(safePage * safeSize, safeSize),
                safePage, safeSize, auditMapper.countAll()
        );
    }

    private UserEntity requireAdmin(String actorId) {
        UserEntity actor = userMapper.findById(actorId);
        if (actor == null) throw new UserNotFoundException();
        if (!"ADMIN".equals(actor.role()) || !"ACTIVE".equals(actor.status())) {
            throw new UserNotFoundException();
        }
        return actor;
    }

    private UserEntity requireUser(String userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) throw new AdminUserNotFoundException();
        return user;
    }

    private void protectLastAdmin(UserEntity target, String nextRole, String nextStatus) {
        boolean removesActiveAdmin = "ADMIN".equals(target.role()) && "ACTIVE".equals(target.status())
                && (!"ADMIN".equals(nextRole) || !"ACTIVE".equals(nextStatus));
        if (removesActiveAdmin && userMapper.countActiveAdmins() <= 1) {
            throw new AdminOperationConflictException("系统必须至少保留一个启用状态的管理员");
        }
    }

    private void audit(String actorId, String targetId, String action, String beforeValue, String afterValue) {
        auditMapper.insert(new AdminAuditEntity(
                UUID.randomUUID().toString(), actorId, targetId, action, beforeValue, afterValue, Instant.now()
        ));
    }

    /** 数据库提交后再清理 Redis；数据库安全版本保证即使缓存清理失败，旧 JWT 也无法通过。 */
    private void invalidateAfterCommit(String userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                userCache.evict(userId);
                principalSecurityService.evict(userId);
                try {
                    refreshTokenService.revokeAll(userId);
                } catch (RuntimeException exception) {
                    // security_version 已经提交，旧访问令牌仍会立即失败；刷新时也会读取数据库新角色/状态。
                    log.warn("Refresh sessions for user {} could not be eagerly revoked: {}", userId, exception.toString());
                }
            }
        });
    }

    private static AdminUserResponse toResponse(UserEntity user) {
        return new AdminUserResponse(
                user.id(), user.username(), user.displayName(), user.role(), user.status(),
                user.securityVersion(), user.region(), user.lastLoginAt(), user.createdAt(), user.updatedAt()
        );
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
