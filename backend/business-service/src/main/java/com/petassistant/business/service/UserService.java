package com.petassistant.business.service;

import java.time.Instant;

import com.petassistant.business.data.dto.request.UpdateUserProfileRequest;
import com.petassistant.business.data.dto.response.CurrentUserResponse;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料服务，负责 MySQL 实体与安全响应之间的转换。 */
@Service
public class UserService {

    private final UserMapper mapper;
    private final UserSessionCacheService cacheService;
    private final OutboxService outboxService;

    public UserService(UserMapper mapper, UserSessionCacheService cacheService, OutboxService outboxService) {
        this.mapper = mapper;
        this.cacheService = cacheService;
        this.outboxService = outboxService;
    }

    /** 当前用户资料优先读取 Redis Hash，未命中时回源 MySQL。 */
    @Transactional(readOnly = true)
    public CurrentUserResponse current(String userId) {
        return cacheService.get(userId).orElseGet(() -> {
            CurrentUserResponse response = toResponse(requireActive(userId));
            cacheService.put(response);
            return response;
        });
    }

    /** 修改资料只允许作用于当前 userId，提交后使旧缓存失效。 */
    @Transactional
    public CurrentUserResponse update(String userId, UpdateUserProfileRequest request) {
        UserEntity current = requireActive(userId);
        String displayName = request.displayName() == null
                ? current.displayName()
                : requireDisplayName(request.displayName());
        String avatarUrl = request.avatarUrl() == null ? current.avatarUrl() : blankToNull(request.avatarUrl());
        String bio = request.bio() == null ? current.bio() : blankToNull(request.bio());
        String region = request.region() == null ? current.region() : blankToNull(request.region());
        mapper.updateProfile(userId, displayName, avatarUrl, bio, region, Instant.now());
        outboxService.record("SEARCH_DOCUMENT", userId, "SEARCH_USER_UPSERT", userId);
        cacheService.evict(userId);
        return toResponse(requireActive(userId));
    }

    /** 登录和刷新都必须确认数据库用户仍为 ACTIVE。 */
    @Transactional(readOnly = true)
    public UserEntity requireActive(String userId) {
        UserEntity user = mapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.status())) {
            throw new UserNotFoundException();
        }
        return user;
    }

    public static CurrentUserResponse toResponse(UserEntity user) {
        return new CurrentUserResponse(
                user.id(), user.username(), user.displayName(), user.role(), user.status(),
                user.avatarUrl(), user.bio(), user.region(), user.lastLoginAt(),
                user.createdAt(), user.updatedAt()
        );
    }

    private static String requireDisplayName(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
