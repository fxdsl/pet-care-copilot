package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员/审核员处理举报；action 决定是否隐藏内容。 */
public record ModerateCommunityReportRequest(
        @NotBlank(message = "处理动作不能为空")
        @Pattern(regexp = "NO_ACTION|HIDE_CONTENT|WARN_USER", message = "处理动作无效")
        String action,
        @Size(max = 1000, message = "处理说明不能超过 1000 个字符")
        String note,
        @Min(value = 1, message = "version 必须是正整数")
        int version
) { }
