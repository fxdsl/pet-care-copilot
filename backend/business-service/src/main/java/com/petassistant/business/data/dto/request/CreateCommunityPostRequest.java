package com.petassistant.business.data.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** 创建社区草稿；媒体必须先由同一用户上传并确认。 */
public record CreateCommunityPostRequest(
        @NotBlank(message = "帖子标题不能为空")
        @Size(max = 160, message = "帖子标题不能超过 160 个字符")
        String title,
        @NotBlank(message = "帖子正文不能为空")
        @Size(max = 10000, message = "帖子正文不能超过 10000 个字符")
        String content,
        String petProfileId,
        String topicId,
        @Size(max = 100, message = "地区不能超过 100 个字符")
        String region,
        @DecimalMin(value = "-90.0", message = "纬度不能小于 -90")
        @DecimalMax(value = "90.0", message = "纬度不能大于 90")
        Double latitude,
        @DecimalMin(value = "-180.0", message = "经度不能小于 -180")
        @DecimalMax(value = "180.0", message = "经度不能大于 180")
        Double longitude,
        @Size(max = 6, message = "每篇帖子最多关联 6 个媒体文件")
        List<String> mediaIds
) { }
