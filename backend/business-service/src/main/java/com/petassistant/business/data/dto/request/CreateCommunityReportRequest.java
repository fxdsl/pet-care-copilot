package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 普通用户提交举报，目标类型与原因使用白名单。 */
public record CreateCommunityReportRequest(
        @NotBlank(message = "举报目标类型不能为空")
        @Pattern(regexp = "POST|COMMENT|USER", message = "举报目标类型无效")
        String targetType,
        @NotBlank(message = "举报目标不能为空")
        String targetId,
        @NotBlank(message = "举报原因不能为空")
        @Pattern(
                regexp = "SPAM|ABUSE|MISINFORMATION|DANGEROUS_ADVICE|ILLEGAL_TRADE|PRIVACY|OTHER",
                message = "举报原因无效"
        )
        String reasonType,
        @Size(max = 1000, message = "举报说明不能超过 1000 个字符")
        String description
) { }
