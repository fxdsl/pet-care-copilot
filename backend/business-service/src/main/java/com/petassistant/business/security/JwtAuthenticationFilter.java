package com.petassistant.business.security;

import java.io.IOException;
import java.util.List;

import com.petassistant.business.service.PrincipalSecurityService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

/** 从 Authorization Bearer 头恢复无状态登录身份。 */
@Component
//OncePerRequestFilter确保每个请求只执行一次（即使请求被转发或包含多次）
//自动应用于所有请求
//这是 Spring 提供的基础过滤器类
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;
    private final JsonSecurityErrorWriter errorWriter;
    private final PrincipalSecurityService principalSecurityService;

    public JwtAuthenticationFilter(
            JwtTokenService tokenService,
            JsonSecurityErrorWriter errorWriter,
            PrincipalSecurityService principalSecurityService
    ) {
        this.tokenService = tokenService;
        this.errorWriter = errorWriter;
        this.principalSecurityService = principalSecurityService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        //从请求头中获取 Authorization Bearer 头
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthenticatedUser principal;
        try {
            // 第2步：解析 JWT Token 得到 AuthenticatedUser 对象，包含用户 ID、用户名、角色、安全版本。
            principal = tokenService.parse(authorization.substring(7).trim());
            // 第3步：检查用户是否当前登录，是否权限是否变化。
            if (!principalSecurityService.isCurrent(principal)) {
                SecurityContextHolder.clearContext();
                errorWriter.write(
                        response,
                        HttpStatus.UNAUTHORIZED.value(),
                        "USER_PERMISSION_CHANGED",
                        "账号状态或权限已经变化，请重新登录"
                );
                return;
            }
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "AUTHORIZATION_UNAVAILABLE",
                    "暂时无法确认账号权限，请稍后重试"
            );
            return;
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(
                    response,
                    HttpStatus.UNAUTHORIZED.value(),
                    "INVALID_ACCESS_TOKEN",
                    "登录凭证无效或已过期，请重新登录"
            );
            return;
        }

        // 第4步：【核心】创建 Authentication 对象并写入 SecurityContext。
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put("userId", principal.userId());
        try {
            // 下游异常由全局业务异常处理器负责，不能误写成“鉴权服务不可用”。
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
