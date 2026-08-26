package com.petassistant.business.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置，集中声明数据访问层位置，避免在每个接口重复添加注解。
 */
@Configuration
@MapperScan("com.petassistant.business.data.mapper")
public class MyBatisConfig {
}
