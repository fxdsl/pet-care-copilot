package com.petassistant.business.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/**
 * 浏览器跨域配置，只允许配置中的 Vue 开发地址访问业务 API。
 */
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public CorsConfig(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    /** 注册业务 API 允许的方法和请求头。 */
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Last-Event-ID")
                .exposedHeaders("WWW-Authenticate");
    }
}
