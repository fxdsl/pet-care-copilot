package com.petassistant.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/** 知识工作流 RabbitMQ 消费者；状态机条件和发布锁共同保证重复消息幂等。 */
@Service
public class KnowledgeWorkflowConsumer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeWorkflowConsumer.class);

    private final ObjectMapper objectMapper;
    private final KnowledgeSubmissionService service;

    public KnowledgeWorkflowConsumer(ObjectMapper objectMapper, KnowledgeSubmissionService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = "${app.knowledge.rabbit-queue}", autoStartup = "${app.knowledge.rabbit-listener-enabled:true}")
    public void consume(String json) throws Exception {
        CommunityEventPayload event = objectMapper.readValue(json, CommunityEventPayload.class);
        try {
            if ("KNOWLEDGE_PRECHECK_REQUESTED".equals(event.eventType())) {
                service.processPrecheck(event.aggregateId());
            } else if ("KNOWLEDGE_PUBLISH_REQUESTED".equals(event.eventType())) {
                service.processPublish(event.aggregateId());
            }
        } catch (RuntimeException error) {
            log.warn("Knowledge workflow {} failed for {}: {}", event.eventType(), event.aggregateId(), error.toString());
            service.markFailed(event.aggregateId(), error.getMessage());
        }
    }
}
