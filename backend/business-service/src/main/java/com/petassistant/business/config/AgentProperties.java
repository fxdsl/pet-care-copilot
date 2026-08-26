package com.petassistant.business.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 第六周 Agent 的知识候选参数。
 * Java 只负责候选数据范围，真正的工具选择、向量排序和循环终止由 FastAPI Agent 完成。
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        int candidateLimit,
        int topK,
        double minScore
) {
}
