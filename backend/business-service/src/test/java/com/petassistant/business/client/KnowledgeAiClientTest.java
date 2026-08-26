package com.petassistant.business.client;

import com.petassistant.business.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** 覆盖 Java 到 FastAPI 投稿预检的真实 HTTP JSON 契约与错误分类。 */
class KnowledgeAiClientTest {

    private MockRestServiceServer server;
    private KnowledgeAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KnowledgeAiClient(builder.build());
    }

    /** 请求和响应都使用 FastAPI 的 snake_case 字段，并完整还原预检结果。 */
    @Test
    void shouldCallPrecheckWithStableSnakeCaseContract() {
        server.expect(requestTo("http://ai-service.test/api/v1/knowledge/precheck"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"title":"幼猫如何养","content":"每天定时喂养。","source_type":"COMMUNITY_POST"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "cleaned_content":"每天定时喂养。",
                          "checksum":"abc123",
                          "summary":"喂养经验",
                          "risk_level":"MEDIUM",
                          "risk_labels":["USER_EXPERIENCE"],
                          "quality_score":65.0
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.precheck("幼猫如何养", "每天定时喂养。", "COMMUNITY_POST");

        assertThat(response.cleanedContent()).isEqualTo("每天定时喂养。");
        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        assertThat(response.riskLabels()).containsExactly("USER_EXPERIENCE");
        server.verify();
    }

    /** 可达服务返回 422 是请求契约错误，不能再误报成服务没有启动。 */
    @Test
    void shouldDistinguishValidationFailureFromUnavailableService() {
        server.expect(requestTo("http://ai-service.test/api/v1/knowledge/precheck"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> client.precheck("幼猫如何养", "每天定时喂养。", "COMMUNITY_POST"))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI 预检请求校验失败（HTTP 422）");
        server.verify();
    }
}
