package com.petassistant.business.config;

import com.petassistant.business.controller.ChatController;
import com.petassistant.business.controller.KnowledgeController;
import com.petassistant.business.controller.AdminKnowledgeSubmissionController;
import com.petassistant.business.security.JsonSecurityErrorWriter;
import com.petassistant.business.security.JwtAuthenticationFilter;
import com.petassistant.business.security.JwtTokenService;
import com.petassistant.business.service.ChatService;
import com.petassistant.business.service.ChatStreamingService;
import com.petassistant.business.service.KnowledgeService;
import com.petassistant.business.service.KnowledgeSubmissionService;
import com.petassistant.business.service.PrincipalSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 第七周 SecurityFilterChain 的登录与管理员角色边界测试。 */
@WebMvcTest({ChatController.class, KnowledgeController.class, AdminKnowledgeSubmissionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JsonSecurityErrorWriter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;
    @MockitoBean
    private ChatStreamingService chatStreamingService;
    @MockitoBean
    private KnowledgeService knowledgeService;
    @MockitoBean
    private KnowledgeSubmissionService knowledgeSubmissionService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private PrincipalSecurityService principalSecurityService;

    @Test
    void shouldRejectAdministratorFromNormalChat() throws Exception {
        mockMvc.perform(post("/api/v1/chat/preview")
                        .with(user("admin-1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"幼猫一天喂几次？\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRejectAdministratorFromPetProfiles() throws Exception {
        mockMvc.perform(get("/api/v1/pet-profiles")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRejectAdministratorFromNormalUserSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search?query=幼猫")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldRequireAuthenticationForChat() throws Exception {
        mockMvc.perform(post("/api/v1/chat/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"幼猫一天喂几次？\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectNormalUserFromKnowledgeManagement() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge-submissions/uploads")
                        .with(user("user-1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validKnowledgeBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowAdministratorToSubmitKnowledgeForReview() throws Exception {
        mockMvc.perform(post("/api/v1/admin/knowledge-submissions/uploads")
                        .with(user("admin-1").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validKnowledgeBody()))
                .andExpect(status().isCreated());
    }

    private static String validKnowledgeBody() {
        return """
                {"title":"幼猫基础喂养","petType":"CAT","category":"FEEDING",
                 "content":"用于权限测试的知识正文","documentType":"TEXT"}
                """;
    }
}
