package com.petassistant.business.config;

import com.petassistant.business.service.RealtimeEventService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 第十周 Redis Pub/Sub 订阅配置；离线消息不依赖本通道。 */
@Configuration
public class RealtimeRedisConfig {

    @Bean
        //connectionFactory	RedisConnectionFactory	Redis 连接工厂（Spring 自动配置）
        // eventService	RealtimeEventService	实时事件服务实例
    RedisMessageListenerContainer realtimeMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeEventService eventService
    ) {
        //创建消息监听容器，用于监听指定通道
        //RedisMessageListenerContainer
        //├── 功能：管理多个 Redis Pub/Sub 监听器
        //├── 生命周期：随 Spring 容器启动/销毁
        //├── 特点：
        //│   ├── 自动重连（连接断开时）
        //│   ├── 线程安全
        //│   └── 支持监听多个频道/模式
        //└── 类比：类似于消息总线的中枢控制器
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        //将 Redis 连接工厂注入到容器中
        //容器通过这个工厂创建与 Redis 的连接
        container.setConnectionFactory(connectionFactory);
        //注册消息监听器
        container.addMessageListener(
                //监听器回调，当收到消息时调用
                (message, pattern) -> eventService.receive(message.toString()),
                //指定订阅的频道
                new ChannelTopic(RealtimeEventService.CHANNEL)
        );
        return container;
    }
}
