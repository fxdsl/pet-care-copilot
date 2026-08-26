package com.petassistant.business.data.dto.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** 更新自己的草稿或已发布帖子；version 用于防止覆盖他人/旧页面编辑结果。 */
public record UpdateCommunityPostRequest(
        @Size(min = 1, max = 160, message = "帖子标题长度应为 1～160")
        String title,
        @Size(min = 1, max = 10000, message = "帖子正文长度应为 1～10000")
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
        List<String> mediaIds,
        @Min(value = 1, message = "version 必须是当前帖子返回的正整数版本")
        int version
) { }
