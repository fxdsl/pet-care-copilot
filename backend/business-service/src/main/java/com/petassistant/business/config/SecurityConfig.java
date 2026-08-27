package com.petassistant.business.config;

import com.petassistant.business.security.JsonSecurityErrorWriter;
import com.petassistant.business.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 第七周统一权限入口：公开认证和健康接口，其余业务默认必须登录。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    //声明一个密码加密 Bean
    //使用 BCrypt 算法，强度为 12（取值范围 4-31，默认 10，12 更安全）
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            JsonSecurityErrorWriter errorWriter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())//禁用跨站请求伪造保护
                .cors(Customizer.withDefaults())//启用跨域资源共享（CORS）
            //设置 Session 创建策略为 STATELESS（无状态）,
            // Spring Security 不会创建或使用 HTTP Session
            //每次请求都必须携带 JWT Token 进行认证
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // SSE 首次请求已经完成 JWT 鉴权；容器后续 ASYNC 调度没有新的 Authorization Header。
                        // 仅放行该调度类型，不能放宽浏览器发起的普通 REQUEST 权限。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        //公开接口
                        // Prometheus 只在容器内网抓取该端点；部署时不能把 8080 直接暴露到公网。
                        .requestMatchers(
                                "/api/v1/auth/**", "/api/v1/system/health",
                                "/actuator/health", "/actuator/health/**", "/actuator/prometheus",
                                "/error", "/ws/**"
                        )
                        .permitAll()
                        .requestMatchers("/api/v1/knowledge/**", "/api/v1/admin/**").hasRole("ADMIN")//管理员专属接口
                        .requestMatchers("/api/v1/moderation/**").hasAnyRole("ADMIN", "MODERATOR")//管理员或版主接口
                        .requestMatchers("/api/v1/search/**").hasAnyRole("USER", "VERIFIED_SELLER")
                        .requestMatchers(
                                "/api/v1/chat/**", "/api/v1/conversations/**", "/api/v1/pet-profiles/**",
                                "/api/v1/messages/**"
                        )
                        .hasAnyRole("USER", "VERIFIED_SELLER")//普通用户和认证卖家接口
                        .requestMatchers("/api/v1/community/**")
                        .hasAnyRole("USER", "VERIFIED_SELLER")
                        .requestMatchers("/api/v1/knowledge-submissions/**")
                        .hasAnyRole("USER", "VERIFIED_SELLER")
                        .anyRequest().authenticated()//以上未匹配的所有其他请求都需要认证（登录）
                )
            //异常处理
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                "AUTHENTICATION_REQUIRED",
                                "请先登录后再使用该功能"
                        ))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(
                                response,
                                HttpStatus.FORBIDDEN.value(),
                                "ACCESS_DENIED",
                                "当前账号没有执行该操作的权限"
                        ))
                )
            // 第6步：添加 JWT 认证过滤器到请求链,在 Spring Security 的默认认证过滤器之前执行 JwtAuthenticationFilter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
