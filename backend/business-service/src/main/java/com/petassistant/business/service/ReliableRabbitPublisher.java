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
        rabbitTemplate.convertAndSend(exchange, routingKey, payloadJson, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setMessageId(eventId);
            message.getMessageProperties().setHeader("eventType", eventType);
            return message;
        });
    }
}
