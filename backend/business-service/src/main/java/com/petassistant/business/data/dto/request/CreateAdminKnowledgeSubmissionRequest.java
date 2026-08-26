package com.petassistant.business.data.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员上传资料时登记来源、作者、有效期和原始正文。 */
public record CreateAdminKnowledgeSubmissionRequest(
        @NotBlank(message = "标题不能为空") @Size(max = 300) String title,
        @Size(max = 200) String sourceName,
        @Size(max = 120) String sourceAuthor,
        @Size(max = 1000) String sourceUrl,
        @Size(max = 255) String fileName,
        @Size(max = 20) String documentType,
        @NotBlank(message = "宠物类型不能为空") @Size(max = 30) String petType,
        @NotBlank(message = "知识分类不能为空") @Size(max = 50) String category,
        @NotBlank(message = "知识正文不能为空") @Size(max = 500000) String content,
        Instant sourcePublishedAt,
        Instant expiresAt
) {
    /** 未填写文件类型时按普通文本处理。 */
    public String resolvedDocumentType() {
        return documentType == null || documentType.isBlank() ? "TEXT" : documentType.trim().toUpperCase();
    }
}
