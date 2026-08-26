package com.petassistant.business.controller;

import java.util.List;

import com.petassistant.business.data.dto.response.PdfExtractResponse;
import com.petassistant.business.security.JwtTokenService;
import com.petassistant.business.security.JsonSecurityErrorWriter;
import com.petassistant.business.service.KnowledgeService;
import com.petassistant.business.service.ChatService;
import com.petassistant.business.service.PrincipalSecurityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** KnowledgeController 保留的 PDF 预览契约测试。 */
@WebMvcTest(KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeService knowledgeService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JsonSecurityErrorWriter jsonSecurityErrorWriter;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private PrincipalSecurityService principalSecurityService;

    /** PDF 提取只是预览，不会绕过第十一周审核状态机。 */
    @Test
    void shouldPreviewPdfWithoutDirectImport() throws Exception {
        when(knowledgeService.extractPdf(any())).thenReturn(new PdfExtractResponse(
                "guide.pdf", "READY", "TEXT", 1, 6, "测试正文", "测试正文", List.of()
        ));

        mockMvc.perform(post("/api/v1/knowledge/documents/pdf/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "guide.pdf",
                                  "contentBase64": "JVBERi0xLjQ="
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }
}
