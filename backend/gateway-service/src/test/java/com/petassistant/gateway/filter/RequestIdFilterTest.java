package com.petassistant.gateway.filter;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/** 请求编号必须同时写入下游请求和上游响应。 */
class RequestIdFilterTest {

    @Test
    void keepsSafeRequestIdAndForwardsIt() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/system/health")
                .header(RequestIdFilter.HEADER, "request-test-1234"));
        AtomicReference<String> forwarded = new AtomicReference<>();

        StepVerifier.create(new RequestIdFilter().filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER));
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get()).isEqualTo("request-test-1234");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo("request-test-1234");
    }
}
