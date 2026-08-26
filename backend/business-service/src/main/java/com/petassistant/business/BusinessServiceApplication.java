package com.petassistant.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * Spring Boot 业务服务入口，组件扫描从当前根包覆盖 controller、service、client 和 data。
 */
public class BusinessServiceApplication {

    /** 启动业务服务并加载 Flyway、MyBatis、Redis 与 HTTP 客户端配置。 */
    public static void main(String[] args) {
        SpringApplication.run(BusinessServiceApplication.class, args);
    }
}
