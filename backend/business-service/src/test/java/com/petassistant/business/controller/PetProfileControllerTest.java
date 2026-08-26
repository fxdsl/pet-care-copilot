package com.petassistant.business.controller;

import java.math.BigDecimal;
import java.time.Instant;

import com.petassistant.business.data.dto.response.PetProfileResponse;
import com.petassistant.business.security.JwtTokenService;
import com.petassistant.business.service.PrincipalSecurityService;
import com.petassistant.business.security.JsonSecurityErrorWriter;
import com.petassistant.business.service.PetProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 宠物档案控制器的请求校验与响应契约测试。 */
@WebMvcTest(PetProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class PetProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PetProfileService service;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private PrincipalSecurityService principalSecurityService;

    @MockitoBean
    private JsonSecurityErrorWriter jsonSecurityErrorWriter;

    /** 合法档案应返回 201，类型和体重保持不变。 */
    @Test
    void shouldCreateValidPetProfile() throws Exception {
        Instant now = Instant.now();
        when(service.create(eq("user-1"), any())).thenReturn(new PetProfileResponse(
                "profile-1", "user-1", "团子", "CAT", "中华田园猫", 5,
                new BigDecimal("2.30"), null, now, now
        ));

        mockMvc.perform(post("/api/v1/pet-profiles")
                        .principal(() -> "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"团子","petType":"CAT","breed":"中华田园猫",
                                 "ageMonths":5,"weightKg":2.30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("团子"))
                .andExpect(jsonPath("$.petType").value("CAT"));
    }

    /** 不支持的宠物类型应在 Controller 层返回 400。 */
    @Test
    void shouldRejectInvalidPetType() throws Exception {
        mockMvc.perform(post("/api/v1/pet-profiles")
                        .principal(() -> "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"团子\",\"petType\":\"BIRD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
