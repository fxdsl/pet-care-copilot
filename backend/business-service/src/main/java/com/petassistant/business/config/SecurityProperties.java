package com.petassistant.business.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 第七周认证配置，集中管理 JWT、刷新令牌和登录保护参数。
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String jwtIssuer = "pet-assistant-business-service";
    private String jwtSecret = "local-dev-change-this-jwt-secret-32-bytes-minimum";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private int loginFailureLimit = 5;
    private Duration loginFailureWindow = Duration.ofMinutes(15);
    private int loginRateLimit = 20;
    private Duration loginRateWindow = Duration.ofMinutes(1);
    private String bootstrapAdminUsername = "";
    private String bootstrapAdminPassword = "";

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public int getLoginFailureLimit() {
        return loginFailureLimit;
    }

    public void setLoginFailureLimit(int loginFailureLimit) {
        this.loginFailureLimit = loginFailureLimit;
    }

    public Duration getLoginFailureWindow() {
        return loginFailureWindow;
    }

    public void setLoginFailureWindow(Duration loginFailureWindow) {
        this.loginFailureWindow = loginFailureWindow;
    }

    public int getLoginRateLimit() {
        return loginRateLimit;
    }

    public void setLoginRateLimit(int loginRateLimit) {
        this.loginRateLimit = loginRateLimit;
    }

    public Duration getLoginRateWindow() {
        return loginRateWindow;
    }

    public void setLoginRateWindow(Duration loginRateWindow) {
        this.loginRateWindow = loginRateWindow;
    }

    public String getBootstrapAdminUsername() {
        return bootstrapAdminUsername;
    }

    public void setBootstrapAdminUsername(String bootstrapAdminUsername) {
        this.bootstrapAdminUsername = bootstrapAdminUsername;
    }

    public String getBootstrapAdminPassword() {
        return bootstrapAdminPassword;
    }

    public void setBootstrapAdminPassword(String bootstrapAdminPassword) {
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }
}
