package com.petassistant.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

/** RabbitMQ 搜索索引消费者；异常交给监听器重试策略处理。 */
@Service
public class SearchIndexConsumer {

    private final ObjectMapper objectMapper;
    private final SearchIndexService service;

    public SearchIndexConsumer(ObjectMapper objectMapper, SearchIndexService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = "${app.search.rabbit-queue}", autoStartup = "${app.search.rabbit-listener-enabled:true}")
    public void consume(String json) throws Exception {
        CommunityEventPayload event = objectMapper.readValue(json, CommunityEventPayload.class);
        MDC.put("eventId", event.eventId());
        try {
            service.process(event);
        } finally {
            MDC.remove("eventId");
        }
    }
}
