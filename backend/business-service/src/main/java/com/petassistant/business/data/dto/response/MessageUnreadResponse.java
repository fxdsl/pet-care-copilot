package com.petassistant.business.data.dto.response;

import java.util.Map;

/** 导航角标和消息中心分类使用的未读计数快照。 */
public record MessageUnreadResponse(long total, long directMessages, Map<String, Long> notifications) {
}
