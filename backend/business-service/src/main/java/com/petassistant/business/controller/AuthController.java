package com.petassistant.business.controller;

import com.petassistant.business.data.dto.request.LoginRequest;
import com.petassistant.business.data.dto.request.LogoutRequest;
import com.petassistant.business.data.dto.request.RefreshTokenRequest;
import com.petassistant.business.data.dto.request.RegisterRequest;
import com.petassistant.business.data.dto.response.AuthTokenResponse;
import com.petassistant.business.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 公开认证入口；业务请求仍必须通过 JWT 过滤器。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.register(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/login")
    public AuthTokenResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        // 登录时，对ip地址进行记录
        return authService.login(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
}
