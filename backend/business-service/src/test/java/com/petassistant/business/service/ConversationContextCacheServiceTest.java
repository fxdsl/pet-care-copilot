package com.petassistant.business.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petassistant.business.data.dto.internal.ConversationContextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ConversationContextCacheService 的序列化、上限与 Redis 故障降级测试。 */
@ExtendWith(MockitoExtension.class)
class ConversationContextCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ConversationContextCacheService service;

    /** 每个测试使用真实 JSON 序列化和固定 TTL。 */
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new ConversationContextCacheService(
                redisTemplate, new ObjectMapper(), Duration.ofMinutes(30)
        );
    }

    /** 缓存 JSON 应恢复为原时间顺序。 */
    @Test
    void shouldDeserializeOrderedContext() {
        when(valueOperations.get("pet-assistant:conversation:context:c1:v1")).thenReturn(
                "[{\"role\":\"USER\",\"content\":\"第一问\"},{\"role\":\"ASSISTANT\",\"content\":\"第一答\"}]"
        );

        Optional<List<ConversationContextMessage>> result = service.get("c1");

        assertThat(result).hasValueSatisfying(messages -> assertThat(messages)
                .extracting(ConversationContextMessage::role)
                .containsExactly("USER", "ASSISTANT"));
    }

    /** 写入超过 12 条时只能保留最后 12 条并设置 30 分钟 TTL。 */
    @Test
    void shouldWriteOnlyLastTwelveMessages() {
        List<ConversationContextMessage> messages = java.util.stream.IntStream.range(0, 14)
                .mapToObj(index -> new ConversationContextMessage("USER", "问题" + index))
                .toList();

        service.put("c1", messages);

        verify(valueOperations).set(
                eq("pet-assistant:conversation:context:c1:v1"),
                org.mockito.ArgumentMatchers.argThat(json -> !json.contains("问题0") && json.contains("问题13")),
                eq(Duration.ofMinutes(30))
        );
    }

    /** Redis 连接异常必须表现为未命中，不能抛给问答链路。 */
    @Test
    void shouldFallbackWhenRedisIsUnavailable() {
        doThrow(new IllegalStateException("redis down")).when(valueOperations).get(any());

        assertThat(service.get("c1")).isEmpty();
    }
}
