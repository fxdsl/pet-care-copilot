package com.petassistant.business.service;

import com.petassistant.business.data.dto.request.RegisterRequest;
import com.petassistant.business.data.entity.UserEntity;
import com.petassistant.business.data.mapper.UserMapper;
import com.petassistant.business.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注册流程的密码、角色和令牌响应测试。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private UserService userService;
    @Mock
    private OutboxService outboxService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new AuthService(
                userMapper, passwordEncoder, jwtTokenService, refreshTokenService,
                loginAttemptService, userService, outboxService
        );
    }

    @Test
    void shouldHashPasswordAndRegisterAsNormalUser() {
        when(jwtTokenService.createAccessToken(any())).thenReturn("access-token");
        when(jwtTokenService.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");

        var response = service.register(
                new RegisterRequest("Alice_01", "very-secret", "爱丽丝"),
                "127.0.0.1"
        );

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        UserEntity inserted = captor.getValue();
        assertThat(inserted.username()).isEqualTo("alice_01");
        assertThat(inserted.role()).isEqualTo("USER");
        assertThat(inserted.passwordHash()).doesNotContain("very-secret");
        assertThat(passwordEncoder.matches("very-secret", inserted.passwordHash())).isTrue();
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }
}
