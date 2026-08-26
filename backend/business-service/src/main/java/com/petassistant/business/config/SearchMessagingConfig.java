package com.petassistant.business.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 搜索索引增量同步与全量重建使用独立持久化队列，避免阻塞社区媒体任务。 */
@Configuration
public class SearchMessagingConfig {

    @Bean
    public DirectExchange searchExchange(SearchProperties properties) {
        return new DirectExchange(properties.rabbitExchange(), true, false);
    }

    @Bean
    public Queue searchIndexQueue(SearchProperties properties) {
        return new Queue(properties.rabbitQueue(), true, false, false);
    }

    @Bean
    public Binding searchIndexBinding(
            Queue searchIndexQueue,
            DirectExchange searchExchange,
            SearchProperties properties
    ) {
        return BindingBuilder.bind(searchIndexQueue).to(searchExchange).with(properties.rabbitRoutingKey());
    }
}
