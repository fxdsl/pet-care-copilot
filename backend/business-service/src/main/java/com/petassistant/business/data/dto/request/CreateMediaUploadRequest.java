package com.petassistant.business.data.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 申请媒体上传地址；确认时会用 MinIO 实际元数据再次校验。 */
public record CreateMediaUploadRequest(
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名不能超过 255 个字符")
        String fileName,
        @NotBlank(message = "Content-Type 不能为空")
        @Size(max = 100, message = "Content-Type 不能超过 100 个字符")
        String contentType,
        @Min(value = 1, message = "文件大小必须大于 0")
        @Max(value = 52428800, message = "文件不能超过 50 MiB")
        long sizeBytes,
        @Pattern(regexp = "^$|[a-fA-F0-9]{64}$", message = "checksumSha256 必须是 64 位十六进制")
        String checksumSha256
) { }
