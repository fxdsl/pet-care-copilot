package com.petassistant.business.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.petassistant.business.data.dto.request.LoginRequest;
import com.petassistant.business.data.dto.request.RegisterRequest;
import com.petassistant.business.data.dto.response.AuthTokenResponse;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.exception.AuthenticationFailedException;
import com.petassistant.business.exception.UsernameAlreadyExistsException;
import com.petassistant.business.security.JwtTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册、登录、刷新和退出编排服务。 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final UserService userService;
    private final OutboxService outboxService;
    private final String fakePasswordHash;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService,
            UserService userService,
            OutboxService outboxService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.userService = userService;
        this.outboxService = outboxService;
        this.fakePasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 注册默认 USER 角色；Redis 无法建立刷新会话时事务回滚，不创建半成品账号。 */
    @Transactional
    public AuthTokenResponse register(RegisterRequest request, String ipAddress) {
        //防止同一 IP 地址频繁请求注册接口
        loginAttemptService.checkEndpointRate("register", ipAddress);
        String username = normalizeUsername(request.username());
        if (userMapper.findByUsername(username) != null) {
            throw new UsernameAlreadyExistsException();
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                UUID.randomUUID().toString(),
                username,
                passwordEncoder.encode(request.password()),
                request.displayName() == null || request.displayName().isBlank()
                        ? username
                        : request.displayName().trim(),
                "USER", "ACTIVE", 1L, null, null, null, now, now, now
        );
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new UsernameAlreadyExistsException();
        }
        outboxService.record("SEARCH_DOCUMENT", user.id(), "SEARCH_USER_UPSERT", user.id());
        return issueTokenPair(user);
    }

    /** 使用恒定的假散列隐藏用户名是否存在，并用 Redis 记录失败次数。 */
    @Transactional
    public AuthTokenResponse login(LoginRequest request, String ipAddress) {
        //防止同一 IP 地址频繁请求登录接口
        loginAttemptService.checkEndpointRate("login", ipAddress);
        //获取用户名,并进行归一化处理
        String username = normalizeUsername(request.username());
        //检查用户登录失败几次，若超过5次，15分钟内该账号禁止登录
        loginAttemptService.requireNotLocked(username);
        //登录密码校验
        UserEntity user = userMapper.findByUsername(username);
        //对密码进行hash处理,并与数据库中的密码进行比较。
        String hash = user == null ? fakePasswordHash : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        //密码校验失败，或者用户状态不是ACTIVE，都抛出异常，记录登录失败次数。
        if (user == null || !passwordMatches || !"ACTIVE".equals(user.status())) {
            loginAttemptService.recordFailure(username);
            throw new AuthenticationFailedException();
        }
        //登录成功，清除登录失败次数记录
        loginAttemptService.clearFailures(username);
        //更新用户最后登录时间
        Instant now = Instant.now();
        userMapper.updateLastLogin(user.id(), now);
        //返回刷新后的用户信息
        UserEntity refreshed = userMapper.findById(user.id());
        return issueTokenPair(refreshed);
    }

    /** 刷新令牌每次使用后立即旋转，旧令牌不能再次使用。 */
    public AuthTokenResponse refresh(String presentedToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(presentedToken);
        try {
            UserEntity user = userService.requireActive(rotated.userId());
            return new AuthTokenResponse(
                    jwtTokenService.createAccessToken(user),
                    rotated.refreshToken(),
                    "Bearer",
                    jwtTokenService.accessTokenExpiresInSeconds(),
                    UserService.toResponse(user)
            );
        } catch (RuntimeException exception) {
            // 用户在令牌有效期内被停用时，撤销刚旋转出来的新令牌。
            refreshTokenService.revoke(rotated.refreshToken());
            throw exception;
        }
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /** 可选本地管理员只在用户名不存在时创建，不覆盖现有账号。 */
    @Transactional
    public boolean bootstrapAdmin(String usernameValue, String password) {
        if (usernameValue == null || password == null || password.length() < 8) {
            return false;
        }
        String username = normalizeUsername(usernameValue);
        if (!username.matches("[a-z0-9_]{4,32}") || userMapper.findByUsername(username) != null) {
            return false;
        }
        Instant now = Instant.now();
        userMapper.insert(new UserEntity(
                UUID.randomUUID().toString(), username, passwordEncoder.encode(password), "系统管理员",
                "ADMIN", "ACTIVE", 1L, null, null, null, null, now, now
        ));
        return true;
    }

    private AuthTokenResponse issueTokenPair(UserEntity user) {
        return new AuthTokenResponse(
                // 生成一个 JSON Web Token (JWT)，用于后续API请求的身份验证,包含用户信息
                jwtTokenService.createAccessToken(user),
                //为用户生成一个长期有效的刷新令牌，存储在Redis中，用于获取新的访问令牌,key设计为`auth:refresh:{tokenId}`
                refreshTokenService.issue(user.id()),
                //令牌类型标识
                "Bearer",
                //访问令牌过期时间，单位秒,告诉前端令牌过期时间，方便前端及时刷新令牌
                jwtTokenService.accessTokenExpiresInSeconds(),
                //将数据库实体 UserEntity 转换为安全的API响应对象，隐藏敏感字段。
                UserService.toResponse(user)
        );
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
