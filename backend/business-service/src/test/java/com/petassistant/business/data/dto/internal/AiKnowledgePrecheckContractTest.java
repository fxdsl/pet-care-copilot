package com.petassistant.business.data.dto.internal;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定 Spring Boot 与 FastAPI 预检接口的 snake_case JSON 契约。 */
class AiKnowledgePrecheckContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Python 必填字段是 source_type，不能误发 Java 属性名 sourceType。 */
    @Test
    void shouldSerializePrecheckRequestWithPythonFieldNames() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new AiKnowledgePrecheckRequest("幼猫如何养", "每天定时喂养。", "COMMUNITY_POST")
        ));

        assertThat(json.path("source_type").asText()).isEqualTo("COMMUNITY_POST");
        assertThat(json.has("sourceType")).isFalse();
    }

    /** FastAPI 的 snake_case 响应必须完整还原，防止预检成功后写入空字段。 */
    @Test
    void shouldDeserializePrecheckResponseWithPythonFieldNames() throws Exception {
        String json = """
                {
                  "cleaned_content": "每天定时喂养。",
                  "checksum": "abc123",
                  "summary": "喂养经验",
                  "risk_level": "MEDIUM",
                  "risk_labels": ["USER_EXPERIENCE"],
                  "quality_score": 65.0
                }
                """;

        AiKnowledgePrecheckResponse response = objectMapper.readValue(json, AiKnowledgePrecheckResponse.class);

        assertThat(response.cleanedContent()).isEqualTo("每天定时喂养。");
        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        assertThat(response.riskLabels()).containsExactly("USER_EXPERIENCE");
        assertThat(response.qualityScore()).isEqualByComparingTo(new BigDecimal("65.0"));
    }
}
