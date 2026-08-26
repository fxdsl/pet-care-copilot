package com.petassistant.business.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 第八周社区事件的持久化交换机、队列和路由绑定。 */
@Configuration
public class CommunityMessagingConfig {

    @Bean
    public DirectExchange communityExchange(@Value("${app.community.rabbit-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue communityMediaQueue(@Value("${app.community.rabbit-queue}") String name) {
        return new Queue(name, true, false, false);
    }

    @Bean
    public Binding communityMediaBinding(
            Queue communityMediaQueue,
            DirectExchange communityExchange,
            @Value("${app.community.rabbit-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(communityMediaQueue).to(communityExchange).with(routingKey);
    }
}
