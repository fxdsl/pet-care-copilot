package com.petassistant.business.security;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.response.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** 将过滤器阶段的 401/403 写成与业务异常一致的 JSON。 */
@Component
public class JsonSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 写入稳定错误契约，禁止输出 JWT 解析细节。 */
    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message, Instant.now()));
    }
}
