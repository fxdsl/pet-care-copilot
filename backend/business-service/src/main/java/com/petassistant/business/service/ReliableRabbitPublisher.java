package com.petassistant.business.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** RabbitMQ 协议适配层；只有带稳定 eventId 的 Outbox 消息允许自动重试。 */
@Service
public class ReliableRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ReliableRabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Retry(name = "rabbitPublisher")
    @CircuitBreaker(name = "rabbitPublisher")
    @Bulkhead(name = "rabbitPublisher", type = Bulkhead.Type.SEMAPHORE)
    public void send(String exchange, String routingKey, String payloadJson, String eventId, String eventType) {
        //exchange	"knowledge.exchange" (假设)	目标交换机
        //routingKey	"knowledge.precheck" (假设)	路由键
        //event.payloadJson()	'{"aggregateId":"sub_789",...}'	消息体（JSON字符串）
        //event.id()	"evt_abc123"	事件ID（用于追踪）
        //event.eventType()	"KNOWLEDGE_PRECHECK_REQUESTED"	事件类型（用于消费者判断）
        rabbitTemplate.convertAndSend(exchange, routingKey, payloadJson, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setMessageId(eventId);
            message.getMessageProperties().setHeader("eventType", eventType);
            return message;
        });
    }
}
