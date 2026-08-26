package com.petassistant.business.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 第十一周知识预检与发布任务使用独立交换机和队列，避免阻塞社区媒体事件。 */
@Configuration
public class KnowledgeMessagingConfig {

    @Bean
    public DirectExchange knowledgeExchange(@Value("${app.knowledge.rabbit-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue knowledgeWorkflowQueue(@Value("${app.knowledge.rabbit-queue}") String name) {
        return new Queue(name, true, false, false);
    }

    @Bean
    public Binding knowledgeWorkflowBinding(
            Queue knowledgeWorkflowQueue,
            DirectExchange knowledgeExchange,
            @Value("${app.knowledge.rabbit-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(knowledgeWorkflowQueue).to(knowledgeExchange).with(routingKey);
    }
}
