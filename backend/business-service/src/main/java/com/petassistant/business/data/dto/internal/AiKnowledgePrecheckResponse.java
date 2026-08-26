package com.petassistant.business.data.dto.internal;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 返回的清洗正文、摘要和风险标签，不包含自动批准结论。 */
public record AiKnowledgePrecheckResponse(
        @JsonProperty("cleaned_content") String cleanedContent,
        String checksum,
        String summary,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("risk_labels") List<String> riskLabels,
        @JsonProperty("quality_score") BigDecimal qualityScore
) { }
