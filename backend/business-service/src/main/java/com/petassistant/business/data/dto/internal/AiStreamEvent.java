package com.petassistant.business.data.dto.internal;

/** FastAPI SSE 的单个脱敏事件；data 保持 JSON 文本，由外层流原样转发。 */
public record AiStreamEvent(String id, String event, String data) {
}
