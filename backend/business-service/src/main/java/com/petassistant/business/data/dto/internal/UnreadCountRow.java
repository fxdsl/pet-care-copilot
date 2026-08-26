package com.petassistant.business.data.dto.internal;

/** MySQL 按通知类型聚合的未读数，用于重建 Redis Hash。 */
public record UnreadCountRow(String type, long count) {
}
