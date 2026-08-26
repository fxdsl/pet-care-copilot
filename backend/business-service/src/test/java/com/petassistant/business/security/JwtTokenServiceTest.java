package com.petassistant.business.security;

import java.time.Instant;

import com.petassistant.business.config.SecurityProperties;
import com.petassistant.business.data.entity.UserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** JWT 签名与身份字段恢复测试。 */
class JwtTokenServiceTest {

    @Test
    void shouldCreateAndParseSignedAccessToken() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret("test-secret-must-have-at-least-32-bytes-value");
        JwtTokenService service = new JwtTokenService(properties);
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                "user-1", "alice", "bcrypt", "爱丽丝", "ADMIN", "ACTIVE", 7L,
                null, null, null, now, now, now
        );

        AuthenticatedUser principal = service.parse(service.createAccessToken(user));

        assertThat(principal.userId()).isEqualTo("user-1");
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.role()).isEqualTo("ADMIN");
        assertThat(principal.securityVersion()).isEqualTo(7L);
        assertThat(service.accessTokenExpiresInSeconds()).isEqualTo(900);
    }
}
