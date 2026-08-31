package com.petassistant.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.CommunityEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
        //将 JSON 字符串转换为 CommunityEventPayload 对象,
        // 包含事件ID、事件类型、聚合ID、事件数据等
        CommunityEventPayload event = objectMapper.readValue(json, CommunityEventPayload.class);
        //将事件ID 放入 MDC 中，方便日志记录和调试
        MDC.put("eventId", event.eventId());
        try {
            //判断是否为预检请求,是则调用预检服务处理，用户使用预检服务检查知识是否符合要求
            if ("KNOWLEDGE_PRECHECK_REQUESTED".equals(event.eventType())) {
                service.processPrecheck(event.aggregateId());

            }
            //判断是否为发布请求,是则调用发布服务处理，管理者使用发布服务发布知识
            else if ("KNOWLEDGE_PUBLISH_REQUESTED".equals(event.eventType())) {
                service.processPublish(event.aggregateId());
            }
        } catch (RuntimeException error) {
            log.warn("Knowledge workflow {} failed for {}: {}", event.eventType(), event.aggregateId(), error.toString());
            service.markFailed(event.aggregateId(), error.getMessage());
        } finally {
            MDC.remove("eventId");
        }
    }
}
