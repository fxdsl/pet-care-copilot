package com.petassistant.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import com.petassistant.business.data.mapper.CommunityMediaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/** 媒体确认后的异步后处理消费者；第八周完成幂等状态推进，缩略图/转码可在此继续扩展。 */
@Service
public class CommunityMediaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommunityMediaEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final CommunityMediaMapper mediaMapper;

    public CommunityMediaEventConsumer(ObjectMapper objectMapper, CommunityMediaMapper mediaMapper) {
        this.objectMapper = objectMapper;
        this.mediaMapper = mediaMapper;
    }

    @RabbitListener(queues = "${app.community.rabbit-queue}", autoStartup = "${app.community.rabbit-listener-enabled:true}")
    public void consume(String json) throws Exception {
        CommunityEventPayload event = objectMapper.readValue(json, CommunityEventPayload.class);
        if ("MEDIA_CONFIRMED".equals(event.eventType())) {
            // 相同事件重复消费只会把同一行再次更新为 READY，结果幂等。
            mediaMapper.markProcessingReady(event.aggregateId());
            log.info("Community media {} async processing marked READY", event.aggregateId());
        }
    }
}
