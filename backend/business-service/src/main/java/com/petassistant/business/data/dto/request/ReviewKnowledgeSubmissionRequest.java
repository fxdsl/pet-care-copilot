package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员审核命令；expectedVersion 用于阻止基于旧内容误审。 */
public record ReviewKnowledgeSubmissionRequest(
        @NotBlank(message = "审核动作不能为空") String action,
        @Min(value = 1, message = "expectedVersion 必须大于 0") int expectedVersion,
        @Size(max = 1) String trustLevel,
        @Size(max = 1000) String reason
) { }
